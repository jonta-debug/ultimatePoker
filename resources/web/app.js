const $ = id => document.getElementById(id);
const tokenKey = 'ultimatePoker.playerToken';
const idKey = 'ultimatePoker.playerId';
const rankLabels = Object.freeze({
  TWO: '2', THREE: '3', FOUR: '4', FIVE: '5', SIX: '6', SEVEN: '7',
  EIGHT: '8', NINE: '9', TEN: '10', JACK: 'J', QUEEN: 'Q', KING: 'K', ACE: 'A'
});
const suitLabels = Object.freeze({
  SPADES: '♠', HEARTS: '♥', DIAMONDS: '♦', CLUBS: '♣'
});

let socket = null;
let reconnectTimer = null;
let socketGeneration = 0;
let currentSnapshot = null;

function setConnection(text, online) {
  $('connection').textContent = text;
  $('connection').className = 'status ' + (online ? 'online' : 'offline');
}

function rankLabel(rank) {
  return rankLabels[rank] || '?';
}

function suitLabel(suit) {
  return suitLabels[suit] || '';
}

function renderCards(target, visible, hidden = 0) {
  target.replaceChildren();
  for (const card of visible || []) {
    const element = document.createElement('div');
    element.className = 'card ' +
      (card.suit === 'HEARTS' || card.suit === 'DIAMONDS' ? 'red' : '');
    element.textContent = rankLabel(card.rank) + suitLabel(card.suit);
    target.append(element);
  }
  for (let index = 0; index < hidden; index++) {
    const element = document.createElement('div');
    element.className = 'card hidden';
    target.append(element);
  }
}

function renderSnapshot(snapshot) {
  if (!snapshot || snapshot.type !== 'state') return;
  // Replace the complete local state. Never merge card arrays from older messages.
  currentSnapshot = snapshot;
  $('game-panel').hidden = false;
  $('join-panel').hidden = true;
  $('stage').textContent = currentSnapshot.statusText;
  $('pot').textContent = 'Pot: ' + Number(currentSnapshot.pot).toFixed(2);

  const storedPlayerId = Number(localStorage.getItem(idKey));
  const viewerPlayerId = currentSnapshot.viewerPlayerId || storedPlayerId;
  const ownPlayer = (currentSnapshot.players || [])
    .find(player => player.playerId === viewerPlayerId);
  renderCards(
    $('hole-cards'),
    ownPlayer ? ownPlayer.visibleCards : [],
    ownPlayer ? ownPlayer.hiddenCardCount : 0
  );
  renderCards(
    $('community-cards'),
    currentSnapshot.communityCards || [],
    currentSnapshot.hiddenCommunityCardCount || 0
  );
  setConnection('Connected', true);
}

function showError(text) {
  $('error').textContent = text || 'Request failed';
}

async function request(path, options = {}) {
  const response = await fetch(path, options);
  const body = await response.json();
  if (!response.ok) throw new Error(body.message || 'Request failed');
  return body;
}

function scheduleReconnect(token, generation) {
  if (generation !== socketGeneration || reconnectTimer) return;
  setConnection('Reconnecting…', false);
  reconnectTimer = window.setTimeout(() => {
    reconnectTimer = null;
    if (generation === socketGeneration) connect(token);
  }, 1500);
}

function connect(token) {
  if (!token) return;
  if (reconnectTimer) {
    window.clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }

  const previous = socket;
  const generation = ++socketGeneration;
  if (previous) {
    previous.onclose = null;
    previous.onerror = null;
    if (previous.readyState === WebSocket.OPEN ||
        previous.readyState === WebSocket.CONNECTING) {
      previous.close();
    }
  }

  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
  const nextSocket = new WebSocket(
    protocol + '//' + location.host + '/ws/player?token=' + encodeURIComponent(token));
  socket = nextSocket;
  setConnection('Connecting…', false);

  nextSocket.onopen = () => {
    if (socket === nextSocket && generation === socketGeneration) {
      setConnection('Connected', true);
    }
  };
  nextSocket.onmessage = event => {
    if (socket !== nextSocket || generation !== socketGeneration) return;
    try {
      const message = JSON.parse(event.data);
      if (message.type === 'state') {
        renderSnapshot(message);
      } else if (message.code === 'REPLACED') {
        showError(message.message);
        nextSocket.onclose = null;
        nextSocket.close();
        setConnection('Connected in another tab', false);
      } else if (message.type === 'error') {
        showError(message.message);
      }
    } catch (error) {
      showError('Received an invalid server update');
    }
  };
  nextSocket.onerror = () => {
    if (socket === nextSocket) setConnection('Connection problem', false);
  };
  nextSocket.onclose = () => {
    if (socket !== nextSocket || generation !== socketGeneration) return;
    socket = null;
    scheduleReconnect(token, generation);
  };
}

async function restore() {
  const token = localStorage.getItem(tokenKey);
  if (!token) return;
  try {
    const data = await request('/api/session', {
      headers: {Authorization: 'Bearer ' + token}
    });
    $('player-name').textContent = data.displayName;
    $('code').value = data.joinCode;
    renderSnapshot(data.state);
    connect(token);
  } catch (error) {
    localStorage.removeItem(tokenKey);
    localStorage.removeItem(idKey);
    showError('Saved seat expired. Join again.');
  }
}

$('join').onclick = async () => {
  showError('');
  try {
    const code = $('code').value.trim().toUpperCase();
    const data = await request('/api/sessions/' + encodeURIComponent(code) + '/join', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({displayName: $('name').value})
    });
    localStorage.setItem(tokenKey, data.playerToken);
    localStorage.setItem(idKey, data.playerId);
    $('player-name').textContent = data.displayName;
    connect(data.playerToken);
  } catch (error) {
    showError(error.message);
  }
};

document.addEventListener('visibilitychange', () => {
  if (document.visibilityState !== 'visible') return;
  const token = localStorage.getItem(tokenKey);
  if (token && (!socket || socket.readyState === WebSocket.CLOSED)) connect(token);
});
window.addEventListener('online', () => {
  const token = localStorage.getItem(tokenKey);
  if (token && (!socket || socket.readyState !== WebSocket.OPEN)) connect(token);
});

restore();
