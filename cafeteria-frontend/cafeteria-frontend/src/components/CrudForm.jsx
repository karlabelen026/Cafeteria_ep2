// Formulario generico de crear/editar (ver docs/EP2_PLAN.md seccion 6.2):
// cada modulo solo declara sus "fields", este componente dibuja los inputs,
// aplica los fieldErrors que devuelve el backend (400) bajo cada campo, y
// expone un footer con Cancelar/Guardar.
export default function CrudForm({ fields, values, onChange, errors, onSubmit, onCancel, submitting, submitLabel }) {
  function setCampo(name, value) {
    onChange({ ...values, [name]: value });
  }

  function errorDe(name) {
    return errors?.find((e) => e.campo === name)?.mensaje;
  }

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        onSubmit();
      }}
    >
      {fields.map((field) => {
        const error = errorDe(field.name);
        const valor = values[field.name] ?? '';

        return (
          <div className="dash-field" key={field.name}>
            <label htmlFor={`campo-${field.name}`}>
              {field.label}
              {field.required && ' *'}
            </label>

            {field.type === 'select' ? (
              <select
                id={`campo-${field.name}`}
                value={valor}
                required={field.required}
                onChange={(e) => setCampo(field.name, e.target.value)}
              >
                <option value="" disabled>
                  Selecciona...
                </option>
                {field.options.map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            ) : field.type === 'checkbox' ? (
              <input
                id={`campo-${field.name}`}
                type="checkbox"
                checked={!!values[field.name]}
                onChange={(e) => setCampo(field.name, e.target.checked)}
              />
            ) : field.type === 'textarea' ? (
              <textarea
                id={`campo-${field.name}`}
                value={valor}
                required={field.required}
                rows={field.rows || 3}
                onChange={(e) => setCampo(field.name, e.target.value)}
              />
            ) : (
              <input
                id={`campo-${field.name}`}
                type={field.type || 'text'}
                value={valor}
                step={field.step}
                min={field.min}
                required={field.required}
                placeholder={field.placeholder}
                max={field.max}
                onChange={(e) => {
                  if (field.type !== 'number') {
                    setCampo(field.name, e.target.value);
                    return;
                  }
                  // 0 es un valor valido (p.ej. stock en 0): solo el campo vacio queda como ''.
                  const numero = e.target.valueAsNumber;
                  setCampo(field.name, Number.isNaN(numero) ? '' : numero);
                }}
              />
            )}

            {error && <span className="dash-field__error">{error}</span>}
          </div>
        );
      })}

      <div className="dash-modal__actions">
        <button type="button" className="dash-btn dash-btn--ghost" onClick={onCancel} disabled={submitting}>
          Cancelar
        </button>
        <button type="submit" className="dash-btn" disabled={submitting}>
          {submitting ? 'Guardando...' : submitLabel || 'Guardar'}
        </button>
      </div>
    </form>
  );
}
