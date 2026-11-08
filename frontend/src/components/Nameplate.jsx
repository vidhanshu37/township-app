// The flat's door plate: tower on the left, big unit number, resident name underneath.
function splitFlat(flatNo) {
  const i = flatNo.indexOf('-');
  return i < 0 ? [flatNo, ''] : [flatNo.slice(0, i), flatNo.slice(i + 1)];
}

export default function Nameplate({ flatNo, name, size = 'lg' }) {
  const [tower, unit] = splitFlat(flatNo);
  const label = `Flat ${flatNo}${name ? `, ${name}` : ''}`;
  return (
    <div className={`plate plate-${size}`} role="img" aria-label={label}>
      <span className="plate-tower" aria-hidden="true">{tower}</span>
      <span className="plate-unit" aria-hidden="true">{unit}</span>
      {size === 'lg' && name ? <span className="plate-name" aria-hidden="true">{name}</span> : null}
    </div>
  );
}
