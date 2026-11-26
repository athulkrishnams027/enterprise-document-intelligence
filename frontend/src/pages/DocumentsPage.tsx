import React, { useRef, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  IconButton,
  InputAdornment,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import CloudUploadOutlinedIcon from '@mui/icons-material/CloudUploadOutlined';
import SearchIcon from '@mui/icons-material/Search';
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import RefreshIcon from '@mui/icons-material/Refresh';
import ForumOutlinedIcon from '@mui/icons-material/ForumOutlined';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { documentsApi } from '../api/services';
import { extractApiErrorMessage } from '../api/client';
import { StatusChip } from '../components/StatusChip';
import { formatBytes, formatDateTime, getFileExtensionBadge } from '../utils/format';
import type { DocumentItem, DocumentStatus } from '../types/api';

export function DocumentsPage() {
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<DocumentStatus | ''>('');
  const [dragActive, setDragActive] = useState(false);
  const [uploadFeedback, setUploadFeedback] = useState<{ severity: 'success' | 'warning' | 'error'; text: string } | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<DocumentItem | null>(null);

  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const { data, isLoading, error } = useQuery({
    queryKey: ['documents', page, rowsPerPage, statusFilter, search],
    queryFn: () => documentsApi.list({ page, size: rowsPerPage, status: statusFilter, search }),
    refetchInterval: (query) => {
      const items = query.state.data?.content ?? [];
      const hasInFlight = items.some((d) => d.status === 'UPLOADED' || d.status === 'PROCESSING');
      return hasInFlight ? 3000 : false;
    },
  });

  const uploadMutation = useMutation({
    mutationFn: async (files: File[]) => {
      if (files.length === 1) {
        const doc = await documentsApi.uploadSingle(files[0]);
        return { uploaded: [doc], failures: [] };
      }
      return documentsApi.uploadMultiple(files);
    },
    onSuccess: (res) => {
      queryClient.invalidateQueries({ queryKey: ['documents'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard-summary'] });
      if (res.failures.length === 0) {
        setUploadFeedback({
          severity: 'success',
          text: `Uploaded ${res.uploaded.length} document(s). Background extraction and embedding started.`,
        });
      } else if (res.uploaded.length > 0) {
        setUploadFeedback({
          severity: 'warning',
          text: `Uploaded ${res.uploaded.length} file(s); ${res.failures.length} failed (${res.failures
            .map((f) => `${f.filename}: ${f.message}`)
            .join('; ')}).`,
        });
      } else {
        setUploadFeedback({
          severity: 'error',
          text: res.failures.map((f) => `${f.filename}: ${f.message}`).join('; '),
        });
      }
    },
    onError: (err) => {
      setUploadFeedback({ severity: 'error', text: extractApiErrorMessage(err, 'Upload failed.') });
    },
  });

  const reprocessMutation = useMutation({
    mutationFn: (id: string) => documentsApi.reprocess(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['documents'] });
      setUploadFeedback({ severity: 'success', text: 'Document queued for reprocessing.' });
    },
    onError: (err) => {
      setUploadFeedback({ severity: 'error', text: extractApiErrorMessage(err, 'Reprocess failed.') });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => documentsApi.remove(id),
    onSuccess: () => {
      setDeleteTarget(null);
      queryClient.invalidateQueries({ queryKey: ['documents'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard-summary'] });
      setUploadFeedback({ severity: 'success', text: 'Document and its vector embeddings deleted.' });
    },
    onError: (err) => {
      setDeleteTarget(null);
      setUploadFeedback({ severity: 'error', text: extractApiErrorMessage(err, 'Deletion failed.') });
    },
  });

  const handleFilesSelected = (fileList: FileList | null) => {
    if (!fileList || fileList.length === 0) return;
    setUploadFeedback(null);
    uploadMutation.mutate(Array.from(fileList));
  };

  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setDragActive(false);
    handleFilesSelected(e.dataTransfer.files);
  };

  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h5">Document Library</Typography>
        <Typography variant="body2" color="text.secondary">
          Upload PDF, DOCX, or TXT files for extraction, chunking, and pgvector indexing.
        </Typography>
      </Box>

      <Card
        onDragOver={(e) => {
          e.preventDefault();
          setDragActive(true);
        }}
        onDragLeave={() => setDragActive(false)}
        onDrop={handleDrop}
        sx={{
          borderStyle: 'dashed',
          borderWidth: 2,
          borderColor: dragActive ? 'primary.main' : 'divider',
          bgcolor: dragActive ? 'action.hover' : 'background.paper',
          transition: 'all 0.15s ease',
        }}
      >
        <CardContent sx={{ py: 4, textAlign: 'center' }}>
          <CloudUploadOutlinedIcon color="primary" sx={{ fontSize: 44 }} />
          <Typography variant="subtitle1" fontWeight={700} sx={{ mt: 1 }}>
            Drag and drop PDF, DOCX, or TXT files here
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Supports single or multiple file uploads (up to 50 MB per file).
          </Typography>
          <input
            ref={fileInputRef}
            type="file"
            hidden
            multiple
            accept=".pdf,.docx,.txt"
            onChange={(e) => {
              handleFilesSelected(e.target.files);
              e.target.value = '';
            }}
          />
          <Button
            variant="contained"
            disabled={uploadMutation.isPending}
            onClick={() => fileInputRef.current?.click()}
          >
            {uploadMutation.isPending ? 'Uploading...' : 'Select Files'}
          </Button>
        </CardContent>
      </Card>

      {uploadFeedback && (
        <Alert severity={uploadFeedback.severity} onClose={() => setUploadFeedback(null)}>
          {uploadFeedback.text}
        </Alert>
      )}

      <Card>
        <CardContent>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2 }}>
            <TextField
              size="small"
              placeholder="Search by filename..."
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setPage(0);
              }}
              sx={{ flexGrow: 1 }}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon fontSize="small" />
                  </InputAdornment>
                ),
              }}
            />
            <FormControl size="small" sx={{ minWidth: 180 }}>
              <InputLabel>Status</InputLabel>
              <Select
                label="Status"
                value={statusFilter}
                onChange={(e) => {
                  setStatusFilter(e.target.value as DocumentStatus | '');
                  setPage(0);
                }}
              >
                <MenuItem value="">All Statuses</MenuItem>
                <MenuItem value="UPLOADED">Uploaded</MenuItem>
                <MenuItem value="PROCESSING">Processing</MenuItem>
                <MenuItem value="COMPLETED">Completed</MenuItem>
                <MenuItem value="FAILED">Failed</MenuItem>
              </Select>
            </FormControl>
          </Stack>

          {isLoading ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 5 }}>
              <CircularProgress />
            </Box>
          ) : error ? (
            <Alert severity="error">{extractApiErrorMessage(error, 'Failed to load documents')}</Alert>
          ) : !data || data.content.length === 0 ? (
            <Alert severity="info">No documents match your current filter.</Alert>
          ) : (
            <>
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Type</TableCell>
                      <TableCell>Filename</TableCell>
                      <TableCell>Size</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell>Pages</TableCell>
                      <TableCell>Chunks</TableCell>
                      <TableCell>Uploaded</TableCell>
                      <TableCell align="right">Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {data.content.map((doc) => (
                      <TableRow key={doc.id} hover>
                        <TableCell>
                          <Chip size="small" label={getFileExtensionBadge(doc.originalFilename)} color="primary" />
                        </TableCell>
                        <TableCell sx={{ fontWeight: 600 }}>
                          {doc.originalFilename}
                          {doc.processingError && (
                            <Typography variant="caption" color="error" display="block">
                              {doc.processingError}
                            </Typography>
                          )}
                        </TableCell>
                        <TableCell>{formatBytes(doc.fileSize)}</TableCell>
                        <TableCell>
                          <StatusChip status={doc.status} />
                        </TableCell>
                        <TableCell>{doc.totalPages !== null ? doc.totalPages : '—'}</TableCell>
                        <TableCell>{doc.totalChunks}</TableCell>
                        <TableCell>{formatDateTime(doc.uploadedAt)}</TableCell>
                        <TableCell align="right">
                          <Stack direction="row" spacing={0.5} justifyContent="flex-end">
                            <Tooltip title="Ask AI about this document">
                              <span>
                                <IconButton
                                  size="small"
                                  color="primary"
                                  disabled={doc.status !== 'COMPLETED'}
                                  onClick={() => navigate(`/chat?documentId=${doc.id}`)}
                                >
                                  <ForumOutlinedIcon fontSize="small" />
                                </IconButton>
                              </span>
                            </Tooltip>
                            <Tooltip title="View details">
                              <IconButton size="small" onClick={() => navigate(`/documents/${doc.id}`)}>
                                <VisibilityOutlinedIcon fontSize="small" />
                              </IconButton>
                            </Tooltip>
                            <Tooltip title="Reprocess document">
                              <span>
                                <IconButton
                                  size="small"
                                  disabled={doc.status === 'PROCESSING' || reprocessMutation.isPending}
                                  onClick={() => reprocessMutation.mutate(doc.id)}
                                >
                                  <RefreshIcon fontSize="small" />
                                </IconButton>
                              </span>
                            </Tooltip>
                            <Tooltip title="Delete document">
                              <span>
                                <IconButton
                                  size="small"
                                  color="error"
                                  disabled={doc.status === 'PROCESSING'}
                                  onClick={() => setDeleteTarget(doc)}
                                >
                                  <DeleteOutlineIcon fontSize="small" />
                                </IconButton>
                              </span>
                            </Tooltip>
                          </Stack>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
              <TablePagination
                component="div"
                count={data.totalElements}
                page={page}
                onPageChange={(_, newPage) => setPage(newPage)}
                rowsPerPage={rowsPerPage}
                onRowsPerPageChange={(e) => {
                  setRowsPerPage(parseInt(e.target.value, 10));
                  setPage(0);
                }}
              />
            </>
          )}
        </CardContent>
      </Card>

      <Dialog open={Boolean(deleteTarget)} onClose={() => setDeleteTarget(null)}>
        <DialogTitle>Delete Document?</DialogTitle>
        <DialogContent>
          <Typography variant="body2">
            Are you sure you want to permanently delete{' '}
            <strong>{deleteTarget?.originalFilename}</strong>? This will also remove all associated vector
            embeddings from pgvector.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteTarget(null)}>Cancel</Button>
          <Button
            color="error"
            variant="contained"
            disabled={deleteMutation.isPending}
            onClick={() => deleteTarget && deleteMutation.mutate(deleteTarget.id)}
          >
            {deleteMutation.isPending ? 'Deleting...' : 'Delete'}
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
