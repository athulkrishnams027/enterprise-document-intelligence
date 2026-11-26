import { useState } from 'react';
import {
  Alert,
  Box,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  Typography,
} from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { adminApi } from '../api/services';
import { extractApiErrorMessage } from '../api/client';
import { formatDateTime } from '../utils/format';

export function AdminPage() {
  const [usersPage, setUsersPage] = useState(0);
  const [auditPage, setAuditPage] = useState(0);

  const { data: dashboard, isLoading, error } = useQuery({
    queryKey: ['admin-dashboard'],
    queryFn: adminApi.dashboard,
  });

  const { data: users } = useQuery({
    queryKey: ['admin-users', usersPage],
    queryFn: () => adminApi.users(usersPage, 10),
  });

  const { data: aiUsage } = useQuery({
    queryKey: ['admin-ai-usage', auditPage],
    queryFn: () => adminApi.aiUsage(auditPage, 10),
  });

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (error || !dashboard) {
    return <Alert severity="error">{extractApiErrorMessage(error, 'Failed to load admin console')}</Alert>;
  }

  const metrics = [
    { label: 'Registered Users', value: dashboard.totalUsers },
    { label: 'Total Documents', value: dashboard.totalDocuments },
    { label: 'Completed Docs', value: dashboard.completedDocuments },
    { label: 'Processing Failures', value: dashboard.failedDocuments },
    { label: 'Total Conversations', value: dashboard.totalConversations },
    { label: 'AI Requests', value: dashboard.totalAiRequests },
    { label: 'Failed AI Calls', value: dashboard.failedAiRequests },
    { label: 'Total Tokens', value: dashboard.totalTokensConsumed },
  ];

  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h5">System Administration &amp; Observability</Typography>
        <Typography variant="body2" color="text.secondary">
          Platform-wide metrics, AI provider diagnostics, registered users, and AI audit logs.
        </Typography>
      </Box>

      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: { xs: 'repeat(2, 1fr)', md: 'repeat(4, 1fr)' },
          gap: 2,
        }}
      >
        {metrics.map((m) => (
          <Card key={m.label}>
            <CardContent>
              <Typography variant="caption" color="text.secondary" fontWeight={600}>
                {m.label}
              </Typography>
              <Typography variant="h4" fontWeight={700} sx={{ mt: 0.5 }}>
                {m.value}
              </Typography>
            </CardContent>
          </Card>
        ))}
      </Box>

      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Active AI Model &amp; Vector Store Configuration
          </Typography>
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
            {Object.entries(dashboard.activeModelInfo).map(([key, val]) => (
              <Chip key={key} label={`${key}: ${String(val)}`} variant="outlined" color="primary" />
            ))}
          </Stack>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Registered Users
          </Typography>
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Name</TableCell>
                  <TableCell>Email</TableCell>
                  <TableCell>Roles</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Created</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {(users?.content ?? []).map((u) => (
                  <TableRow key={u.id}>
                    <TableCell sx={{ fontWeight: 600 }}>{u.fullName}</TableCell>
                    <TableCell>{u.email}</TableCell>
                    <TableCell>
                      <Stack direction="row" spacing={0.5}>
                        {u.roles.map((r) => (
                          <Chip key={r} size="small" label={r} color={r === 'ADMIN' ? 'secondary' : 'default'} />
                        ))}
                      </Stack>
                    </TableCell>
                    <TableCell>{u.enabled ? 'Active' : 'Disabled'}</TableCell>
                    <TableCell>{formatDateTime(u.createdAt)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          {users && (
            <TablePagination
              component="div"
              count={users.totalElements}
              page={usersPage}
              onPageChange={(_, p) => setUsersPage(p)}
              rowsPerPage={10}
              rowsPerPageOptions={[10]}
            />
          )}
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            AI Audit &amp; Usage Logs
          </Typography>
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Operation</TableCell>
                  <TableCell>Provider / Model</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Tokens</TableCell>
                  <TableCell>Duration</TableCell>
                  <TableCell>Timestamp</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {(aiUsage?.content ?? []).map((log) => (
                  <TableRow key={log.id}>
                    <TableCell sx={{ fontWeight: 600 }}>{log.operation}</TableCell>
                    <TableCell>
                      {log.provider} / {log.model}
                    </TableCell>
                    <TableCell>
                      <Chip
                        size="small"
                        label={log.status}
                        color={log.status === 'FAILED' ? 'error' : log.status === 'SUCCESS' ? 'success' : 'warning'}
                        variant="outlined"
                      />
                    </TableCell>
                    <TableCell>{log.totalTokens ?? '—'}</TableCell>
                    <TableCell>{log.durationMs} ms</TableCell>
                    <TableCell>{formatDateTime(log.createdAt)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          {aiUsage && (
            <TablePagination
              component="div"
              count={aiUsage.totalElements}
              page={auditPage}
              onPageChange={(_, p) => setAuditPage(p)}
              rowsPerPage={10}
              rowsPerPageOptions={[10]}
            />
          )}
        </CardContent>
      </Card>
    </Stack>
  );
}
