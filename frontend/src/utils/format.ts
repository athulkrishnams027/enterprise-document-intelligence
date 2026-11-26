export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  const kb = bytes / 1024;
  if (kb < 1024) return `${kb.toFixed(1)} KB`;
  const mb = kb / 1024;
  return `${mb.toFixed(2)} MB`;
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleString();
}

export function getFileExtensionBadge(filename: string): string {
  const idx = filename.lastIndexOf('.');
  if (idx < 0) return 'DOC';
  return filename.substring(idx + 1).toUpperCase();
}
