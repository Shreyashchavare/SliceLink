import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './styles.css'

function App() {
  return (
    <main>
      <h1>SliceLink</h1>
      <p>Scalable URL shortening, built incrementally.</p>
      <p>Phase 1 foundation — features are coming soon.</p>
    </main>
  )
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
