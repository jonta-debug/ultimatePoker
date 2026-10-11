'use strict';

const fs = require('fs');
const vm = require('vm');

const source = fs.readFileSync('resources/web/app.js', 'utf8');
const mappingSource = source.slice(0, source.indexOf('let socket'));
const context = {};
vm.createContext(context);
vm.runInContext(mappingSource + '; result = {rankLabels, suitLabels};', context);

const expectedRanks = {
  TWO: '2', THREE: '3', FOUR: '4', FIVE: '5', SIX: '6', SEVEN: '7',
  EIGHT: '8', NINE: '9', TEN: '10', JACK: 'J', QUEEN: 'Q', KING: 'K', ACE: 'A'
};
const expectedSuits = {
  SPADES: '♠', HEARTS: '♥', DIAMONDS: '♦', CLUBS: '♣'
};

for (const [rank, label] of Object.entries(expectedRanks)) {
  if (context.result.rankLabels[rank] !== label) {
    throw new Error(`${rank} should render as ${label}`);
  }
}
for (const [suit, label] of Object.entries(expectedSuits)) {
  if (context.result.suitLabels[suit] !== label) {
    throw new Error(`${suit} should render as ${label}`);
  }
}
if (context.result.rankLabels.THREE === context.result.rankLabels.TEN) {
  throw new Error('THREE and TEN must never share a mobile label');
}

console.log('mobile-card-rendering.test.js passed: 18 assertions');
