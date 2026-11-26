import { Chip } from '@mui/material';
import type { DocumentStatus } from '../types/api';

const STATUS_MAP: Record<DocumentStatus, { label: string; color: 'default' | 'info' | 'success' | 'error' | 'warning' }> = {
  UPLOADED: { label: 'Uploaded', color: 'info' },
  PROCESSING: { label: 'Processing', color: 'warning' },
  COMPLETED: { label: 'Completed', color: 'success' },
  FAILED: { label: 'Failed', color: 'error' },
  DELETING: { label: 'Deleting', color: 'default' },
};

export function StatusChip({ status }: { status: DocumentStatus }) {
  const cfg = STATUS_MAP[status] ?? { label: status, color: 'default' };
  return <Chip size="small" label={cfg.label} color={cfg.color} variant="outlined" sx={{ fontWeight: 600 }} />;
}
