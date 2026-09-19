export default function BootScreen({ title, body, action }) {
  return (
    <div className="boot">
      <div className="boot-card">
        <div className="brand">
          <span className="brand-mark">A</span>
          <span className="brand-word">Akeza</span>
        </div>
        {!action && <div className="spinner" aria-hidden="true" />}
        <h1>{title}</h1>
        {body && <p>{body}</p>}
        {action}
      </div>
    </div>
  )
}
