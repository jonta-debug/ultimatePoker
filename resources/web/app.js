const $ = id => document.getElementById(id);
const tokenKey = 'ultimatePoker.playerToken';
const idKey = 'ultimatePoker.playerId';
const rankLabels = Object.freeze({
  TWO: '2', THREE: '3', FOUR: '4', FIVE: '5', SIX: '6', SEVEN: '7',
  EIGHT: '8', NINE: '9', TEN: '10', JACK: 'J', QUEEN: 'Q', KING: 'K', ACE: 'A'
});
const suitLabels = Object.freeze({SPADES: '♠', HEARTS: '♥', DIAMONDS: '♦', CLUBS: '♣'});

let socket = null;
let reconnectTimer = null;
let socketGeneration = 0;
let currentSnapshot = null;
let joinInFlight = false;

function setConnection(text, online) {
  $('connection').textContent = text;
  $('connection').className = 'status ' + (online ? 'online' : 'offline');
}

function rankLabel(rank) { return rankLabels[rank] || '?'; }
function suitLabel(suit) { return suitLabels[suit] || ''; }

function renderCards(target, visible, hidden = 0) {
  target.replaceChildren();
  for (const card of visible || []) {
    const element = document.createElement('div');
    element.className = 'card ' + (
      card.suit === 'HEARTS' || card.suit === 'DIAMONDS' ? 'red' : '');
    element.textContent = rankLabel(card.rank) + suitLabel(card.suit);
    target.append(element);
  }
  for (let i = 0; i < hidden; i++) {
    const element = document.createElement('div');
    element.className = 'card hidden';
    target.append(element);
  }
}

function viewerPlayer(snapshot) {
  const id = snapshot.viewerPlayerId || Number(localStorage.getItem(idKey));
  return (snapshot.players || []).find(player => player.playerId === id);
}

function renderHoldem(snapshot, ownPlayer) {
  const holdem = snapshot.gameMode === 'TEXAS_HOLDEM';
  $('holdem-info').hidden = !holdem;
  if (!holdem) return;
  const actor = (snapshot.players || []).find(
    player => player.playerId === snapshot.actingPlayerId);
  $('turn-status').textContent = snapshot.stage === 'SHOWDOWN'
    ? 'Hand complete'
    : actor ? 'Current player: ' + actor.displayName : 'Waiting';
  $('call-status').textContent = ownPlayer
    ? 'To call: ' + ownPlayer.amountToCall
      + ' · Minimum raise to: ' + ownPlayer.minimumRaiseTo
    : '';
  const legal = new Set(ownPlayer?.legalActions || []);
  for (const button of document.querySelectorAll('#holdem-actions button')) {
    button.disabled = !legal.has(button.dataset.action);
  }
}

function renderSnapshot(snapshot) {
  if (!snapshot || snapshot.type !== 'state') return;
  currentSnapshot = snapshot;
  $('game-panel').hidden = false;
  $('join-panel').hidden = true;
  $('stage').textContent = snapshot.statusText;
  $('pot').textContent = 'Pot: ' + Number(snapshot.pot).toFixed(
    snapshot.gameMode === 'TEXAS_HOLDEM' ? 0 : 2);
  const ownPlayer = viewerPlayer(snapshot);
  renderCards($('hole-cards'), ownPlayer ? ownPlayer.visibleCards : [],
    ownPlayer ? ownPlayer.hiddenCardCount : 0);
  renderCards($('community-cards'), snapshot.communityCards || [],
    snapshot.hiddenCommunityCardCount || 0);
  renderHoldem(snapshot, ownPlayer);
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
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null;
    if (generation === socketGeneration) connect(token);
  }, 1500);
}

function connect(token) {
  if (!token) return;
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
  const previous = socket;
  const generation = ++socketGeneration;
  if (previous) {
    previous.onclose = null;
    previous.onerror = null;
    if (previous.readyState === WebSocket.OPEN
        || previous.readyState === WebSocket.CONNECTING) previous.close();
  }
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
  const next = new WebSocket(protocol + '//' + location.host
    + '/ws/player?token=' + encodeURIComponent(token));
  socket = next;
  setConnection('Connecting…', false);
  next.onopen = () => {
    if (socket === next && generation === socketGeneration) {
      setConnection('Connected', true);
    }
  };
  next.onmessage = event => {
    if (socket !== next || generation !== socketGeneration) return;
    try {
      const message = JSON.parse(event.data);
      if (message.type === 'state') renderSnapshot(message);
      else if (message.code === 'REPLACED') {
        showError(message.message);
        next.onclose = null;
        next.close();
        setConnection('Connected in another tab', false);
      } else if (message.type === 'error') showError(message.message);
    } catch (error) {
      showError('Received an invalid server update');
    }
  };
  next.onerror = () => {
    if (socket === next) setConnection('Connection problem', false);
  };
  next.onclose = () => {
    if (socket !== next || generation !== socketGeneration) return;
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
  if (joinInFlight) return;
  joinInFlight = true;
  $('join').disabled = true;
  showError('');
  try {
    const code = $('code').value.trim().toUpperCase();
    const savedToken = localStorage.getItem(tokenKey);
    const headers = {'Content-Type': 'application/json'};
    if (savedToken) headers.Authorization = 'Bearer ' + savedToken;
    const data = await request('/api/sessions/' + encodeURIComponent(code) + '/join', {
      method: 'POST',
      headers,
      body: JSON.stringify({displayName: $('name').value})
    });
    localStorage.setItem(tokenKey, data.playerToken);
    localStorage.setItem(idKey, data.playerId);
    $('player-name').textContent = data.displayName;
    connect(data.playerToken);
  } catch (error) {
    showError(error.message);
  } finally {
    joinInFlight = false;
    $('join').disabled = false;
  }
};

for (const button of document.querySelectorAll('#holdem-actions button')) {
  button.onclick = async () => {
    const token = localStorage.getItem(tokenKey);
    const action = button.dataset.action;
    const amount = Number($('action-amount').value || 0);
    try {
      const state = await request('/api/holdem/action', {
        method: 'POST',
        headers: {'Content-Type': 'application/json', Authorization: 'Bearer ' + token},
        body: JSON.stringify({action, amount})
      });
      renderSnapshot(state);
      $('action-amount').value = '';
    } catch (error) {
      showError(error.message);
    }
  };
}

document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'visible') {
    const token = localStorage.getItem(tokenKey);
    if (token && (!socket || socket.readyState === WebSocket.CLOSED)) connect(token);
  }
});

window.addEventListener('online', () => {
  const token = localStorage.getItem(tokenKey);
  if (token && (!socket || socket.readyState !== WebSocket.OPEN)) connect(token);
});

restore();
