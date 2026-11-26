import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CircularProgress,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import DescriptionOutlinedIcon from '@mui/icons-material/DescriptionOutlined';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import HourglassTopIcon from '@mui/icons-material/HourglassTop';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import ForumOutlinedIcon from '@mui/icons-material/ForumOutlined';
import CloudUploadOutlinedIcon from '@mui/icons-material/CloudUploadOutlined';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { dashboardApi } from '../api/services';
import { extractApiErrorMessage } from '../api/client';
import { StatusChip } from '../components/StatusChip';
import { formatBytes, formatDateTime } from '../utils/format';

export function DashboardPage() {
  const navigate = useNavigate();
  const { data, isLoading, error } = useQuery({
    queryKey: ['dashboard-summary'],
    queryFn: dashboardApi.summary,
    refetchInterval: 10000,
  });

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (error) {
    return <Alert severity="error">{extractApiErrorMessage(error, 'Failed to load dashboard')}</Alert>;
  }

  const kpis = [
    {
      label: 'Total Documents',
      value: data?.totalDocuments ?? 0,
      icon: <DescriptionOutlinedIcon color="primary" />,
    },
    {
      label: 'Completed',
      value: data?.completedDocuments ?? 0,
      icon: <CheckCircleOutlineIcon color="success" />,
    },
    {
      label: 'Processing',
      value: data?.processingDocuments ?? 0,
      icon: <HourglassTopIcon color="warning" />,
    },
    {
      label: 'Failed',
      value: data?.failedDocuments ?? 0,
      icon: <ErrorOutlineIcon color="error" />,
    },
    {
      label: 'Conversations',
      value: data?.totalConversations ?? 0,
      icon: <ForumOutlinedIcon color="info" />,
    },
  ];

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems={{ sm: 'center' }} gap={2}>
        <Box>
          <Typography variant="h5">Workspace Overview</Typography>
          <Typography variant="body2" color="text.secondary">
            Monitor your indexed knowledge base and start grounded AI conversations.
          </Typography>
        </Box>
        <Stack direction="row" spacing={1.5}>
          <Button
            variant="outlined"
            startIcon={<CloudUploadOutlinedIcon />}
            onClick={() => navigate('/documents')}
          >
            Upload Documents
          </Button>
          <Button
            variant="contained"
            startIcon={<ForumOutlinedIcon />}
            onClick={() => navigate('/chat')}
          >
            Ask AI Workspace
          </Button>
        </Stack>
      </Stack>

      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: { xs: 'repeat(2, 1fr)', md: 'repeat(5, 1fr)' },
          gap: 2,
        }}
      >
        {kpis.map((kpi) => (
          <Card key={kpi.label}>
            <CardContent>
              <Stack direction="row" justifyContent="space-between" alignItems="center">
                <Typography variant="caption" color="text.secondary" fontWeight={600}>
                  {kpi.label}
                </Typography>
                {kpi.icon}
              </Stack>
              <Typography variant="h4" fontWeight={700} sx={{ mt: 1 }}>
                {kpi.value}
              </Typography>
            </CardContent>
          </Card>
        ))}
      </Box>

      <Card>
        <CardContent>
          <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
            <Typography variant="h6">Recent Document Activity</Typography>
            <Button size="small" onClick={() => navigate('/documents')}>
              View All
            </Button>
          </Stack>
          {!data?.recentDocuments || data.recentDocuments.length === 0 ? (
            <Alert severity="info">
              No documents uploaded yet. Go to the Document Library to upload PDF, DOCX, or TXT files.
            </Alert>
          ) : (
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Document</TableCell>
                    <TableCell>Size</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Chunks</TableCell>
                    <TableCell>Uploaded</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {data.recentDocuments.map((doc) => (
                    <TableRow key={doc.id} hover>
                      <TableCell sx={{ fontWeight: 600 }}>{doc.originalFilename}</TableCell>
                      <TableCell>{formatBytes(doc.fileSize)}</TableCell>
                      <TableCell>
                        <StatusChip status={doc.status} />
                      </TableCell>
                      <TableCell>{doc.totalChunks}</TableCell>
                      <TableCell>{formatDateTime(doc.uploadedAt)}</TableCell>
                      <TableCell align="right">
                        <Button size="small" onClick={() => navigate(`/documents/${doc.id}`)}>
                          Details
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </CardContent>
      </Card>
    </Stack>
  );
}
