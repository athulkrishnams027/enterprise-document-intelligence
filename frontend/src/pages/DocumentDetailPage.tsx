import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Divider,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import RefreshIcon from '@mui/icons-material/Refresh';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import ForumOutlinedIcon from '@mui/icons-material/ForumOutlined';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import { documentsApi } from '../api/services';
import { extractApiErrorMessage } from '../api/client';
import { StatusChip } from '../components/StatusChip';
import { formatBytes, formatDateTime } from '../utils/format';

export function DocumentDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const { data, isLoading, error } = useQuery({
    queryKey: ['document-detail', id],
    queryFn: () => documentsApi.details(id!),
    enabled: Boolean(id),
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status === 'UPLOADED' || status === 'PROCESSING' ? 2500 : false;
    },
  });

  const reprocessMutation = useMutation({
    mutationFn: () => documentsApi.reprocess(id!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['document-detail', id] });
      queryClient.invalidateQueries({ queryKey: ['documents'] });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: () => documentsApi.remove(id!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['documents'] });
      navigate('/documents');
    },
  });

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (error || !data) {
    return (
      <Stack spacing={2}>
        <Button startIcon={<ArrowBackIcon />} onClick={() => navigate('/documents')} sx={{ alignSelf: 'flex-start' }}>
          Back to Library
        </Button>
        <Alert severity="error">{extractApiErrorMessage(error, 'Document not found.')}</Alert>
      </Stack>
    );
  }

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems={{ sm: 'center' }} gap={2}>
        <Stack direction="row" spacing={1.5} alignItems="center">
          <Button startIcon={<ArrowBackIcon />} onClick={() => navigate('/documents')}>
            Back
          </Button>
          <Typography variant="h5" noWrap>
            {data.originalFilename}
          </Typography>
          <StatusChip status={data.status} />
        </Stack>
        <Stack direction="row" spacing={1}>
          <Button
            variant="contained"
            startIcon={<ForumOutlinedIcon />}
            disabled={data.status !== 'COMPLETED'}
            onClick={() => navigate(`/chat?documentId=${data.id}`)}
          >
            Chat with Document
          </Button>
          <Button
            variant="outlined"
            startIcon={<RefreshIcon />}
            disabled={data.status === 'PROCESSING' || reprocessMutation.isPending}
            onClick={() => reprocessMutation.mutate()}
          >
            Reprocess
          </Button>
          <Button
            variant="outlined"
            color="error"
            startIcon={<DeleteOutlineIcon />}
            disabled={data.status === 'PROCESSING' || deleteMutation.isPending}
            onClick={() => deleteMutation.mutate()}
          >
            Delete
          </Button>
        </Stack>
      </Stack>

      {data.processingError && (
        <Alert severity="error">
          <strong>Processing Error:</strong> {data.processingError}
        </Alert>
      )}

      {(reprocessMutation.error || deleteMutation.error) && (
        <Alert severity="error">
          {extractApiErrorMessage(reprocessMutation.error || deleteMutation.error)}
        </Alert>
      )}

      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Document Metadata &amp; Extraction Summary
          </Typography>
          <Divider sx={{ mb: 2 }} />
          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', md: 'repeat(3, 1fr)' },
              gap: 2.5,
            }}
          >
            <Box>
              <Typography variant="caption" color="text.secondary">MIME Type</Typography>
              <Typography variant="body2" fontWeight={600}>{data.mimeType}</Typography>
            </Box>
            <Box>
              <Typography variant="caption" color="text.secondary">File Size</Typography>
              <Typography variant="body2" fontWeight={600}>{formatBytes(data.fileSize)}</Typography>
            </Box>
            <Box>
              <Typography variant="caption" color="text.secondary">Total Pages</Typography>
              <Typography variant="body2" fontWeight={600}>
                {data.totalPages !== null ? data.totalPages : 'N/A (Non-paginated format)'}
              </Typography>
            </Box>
            <Box>
              <Typography variant="caption" color="text.secondary">Indexed Vector Chunks</Typography>
              <Typography variant="body2" fontWeight={600}>{data.totalChunks}</Typography>
            </Box>
            <Box>
              <Typography variant="caption" color="text.secondary">Uploaded At</Typography>
              <Typography variant="body2" fontWeight={600}>{formatDateTime(data.uploadedAt)}</Typography>
            </Box>
            <Box>
              <Typography variant="caption" color="text.secondary">Last Processed</Typography>
              <Typography variant="body2" fontWeight={600}>{formatDateTime(data.lastProcessedAt)}</Typography>
            </Box>
          </Box>
          <Box sx={{ mt: 2.5 }}>
            <Typography variant="caption" color="text.secondary">SHA-256 Checksum</Typography>
            <Typography variant="body2" fontFamily="monospace" sx={{ wordBreak: 'break-all' }}>
              {data.fileChecksum}
            </Typography>
          </Box>
          {data.extractionSummary && (
            <Box sx={{ mt: 2 }}>
              <Typography variant="caption" color="text.secondary">Extraction Details</Typography>
              <Typography variant="body2">{data.extractionSummary}</Typography>
            </Box>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Processing Audit Trail
          </Typography>
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Stage</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Details</TableCell>
                  <TableCell>Duration</TableCell>
                  <TableCell>Timestamp</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {data.processingLogs.map((log) => (
                  <TableRow key={log.id}>
                    <TableCell sx={{ fontWeight: 600 }}>{log.stage}</TableCell>
                    <TableCell>
                      <Chip
                        size="small"
                        label={log.status}
                        color={log.status === 'FAILED' ? 'error' : log.status === 'COMPLETED' ? 'success' : 'info'}
                        variant="outlined"
                      />
                    </TableCell>
                    <TableCell>{log.message ?? '—'}</TableCell>
                    <TableCell>{log.durationMs !== null ? `${log.durationMs} ms` : '—'}</TableCell>
                    <TableCell>{formatDateTime(log.createdAt)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </CardContent>
      </Card>
    </Stack>
  );
}
