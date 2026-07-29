import React, { useState } from 'react';
import { AppShell, Button, SideBrand, SideNav, StatusPill } from './design-system';
import CardBoard from './components/CardBoard.jsx';
import CardDetail from './components/CardDetail.jsx';
import FailedQueue from './components/FailedQueue.jsx';
import BureauPanel from './components/BureauPanel.jsx';
import IssuingConfig from './components/IssuingConfig.jsx';

const SCREENS = [
  { id: 'board', label: 'Card Board' },
  { id: 'queue', label: 'Failed-Issue Queue' },
  { id: 'bureau', label: 'Bureau Control Panel' },
  { id: 'config', label: 'Issuing Config' },
];

export default function App() {
  const [screen, setScreen] = useState('board');
  const [selectedCard, setSelectedCard] = useState(null);

  function handleSelectCard(card) {
    setSelectedCard(card);
    setScreen('detail');
  }

  function handleBackToBoard() {
    setSelectedCard(null);
    setScreen('board');
  }

  return (
    <AppShell
      side={
        <>
          <SideBrand
            brand="Team 08"
            product="Card Issuing"
            meta="neo08 · card"
          />
          <SideNav items={SCREENS} active={screen} onSelect={setScreen} />
          <div className="app-side-status">
            <StatusPill tone="positive">Up</StatusPill>
          </div>
        </>
      }
      footer="Module 08 · Card Issuing · applications arrive from the orchestrator, never from this UI"
    >
      {screen === 'board' && (
        <CardBoard onSelectCard={handleSelectCard} />
      )}
      {screen === 'detail' && (
        <CardDetail selectedCard={selectedCard} onBack={handleBackToBoard} />
      )}
      {screen === 'queue' && <FailedQueue />}
      {screen === 'bureau' && <BureauPanel />}
      {screen === 'config' && <IssuingConfig />}
    </AppShell>
  );
}
