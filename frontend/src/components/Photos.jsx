import { useEffect, useState } from 'react';
import { api, store } from '../api.js';

export const MAX_PHOTOS = 3;

export function shrinkImage(file, maxSide = 1280) {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => {
      const scale = Math.min(1, maxSide / Math.max(img.width, img.height));
      const canvas = document.createElement('canvas');
      canvas.width = Math.round(img.width * scale);
      canvas.height = Math.round(img.height * scale);
      canvas.getContext('2d').drawImage(img, 0, 0, canvas.width, canvas.height);
      URL.revokeObjectURL(url);
      canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error('Could not read that photo.'))), 'image/jpeg', 0.82);
    };
    img.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error('That file is not a photo we can read.'));
    };
    img.src = url;
  });
}

export async function uploadPhotos(complaintId, files) {
  let failed = 0;
  for (const file of files.slice(0, MAX_PHOTOS)) {
    try {
      const blob = await shrinkImage(file);
      const form = new FormData();
      form.append('file', blob, 'photo.jpg');
      await api(`/api/resident/complaints/${complaintId}/photos`, { method: 'POST', formData: form });
    } catch {
      failed += 1;
    }
  }
  return failed;
}

export function PhotoStrip({ base }) {
  const [photos, setPhotos] = useState([]);

  useEffect(() => {
    let alive = true;
    const urls = [];
    (async () => {
      try {
        const list = await api(`${base}/photos`);
        const token = store.read()?.token;
        const loaded = [];
        for (const p of list || []) {
          const res = await fetch(`${base}/photos/${p.id}`, { headers: token ? { Authorization: `Bearer ${token}` } : {} });
          if (!res.ok) continue;
          const url = URL.createObjectURL(await res.blob());
          urls.push(url);
          loaded.push({ id: p.id, url });
        }
        if (alive) setPhotos(loaded);
      } catch {
      }
    })();
    return () => {
      alive = false;
      urls.forEach((u) => URL.revokeObjectURL(u));
    };
  }, [base]);

  if (photos.length === 0) return null;
  return (
    <div className="photo-strip">
      {photos.map((p, i) => (
        <a key={p.id} href={p.url} target="_blank" rel="noreferrer">
          <img src={p.url} alt={`Photo ${i + 1} of the problem`} />
        </a>
      ))}
    </div>
  );
}
