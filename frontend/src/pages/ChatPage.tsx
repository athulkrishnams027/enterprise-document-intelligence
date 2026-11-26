import React, { useEffect, useRef, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControl,
  IconButton,
  InputLabel,
  List,
  ListItemButton,
  ListItemText,
  MenuItem,
  Paper,
  Select,
  Slider,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import AddCommentOutlinedIcon from '@mui/icons-material/AddCommentOutlined';
import SendIcon from '@mui/icons-material/Send';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import TuneOutlinedIcon from '@mui/icons-material/TuneOutlined';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import VerifiedOutlinedIcon from '@mui/icons-material/VerifiedOutlined';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { conversationsApi, documentsApi, ragApi } from '../api/services';
import { extractApiErrorMessage } from '../api/client';
import { CitationCards } from '../components/CitationDrawer';
import type { ConversationItem } from '../types/api';

export function ChatPage() {
  const { conversationId } = useParams<{ conversationId?: string }>();
  const [searchParams] = useSearchParams();
  const initialDocId = searchParams.get('documentId') || '';

  const [selectedDocId, setSelectedDocId] = useState<string>(initialDocId);
  const [question, setQuestion] = useState('');
  const [showTuning, setShowTuning] = useState(false);
  const [topK, setTopK] = useState(6);
  const [similarityThreshold, setSimilarityThreshold] = useState(0.55);
  const [temperature, setTemperature] = useState(0.2);
  const [renameTarget, setRenameTarget] = useState<ConversationItem | null>(null);
  const [renameTitle, setRenameTitle] = useState('');
  const [chatError, setChatError] = useState<string | null>(null);

  const messagesEndRef = useRef<HTMLDivElement | null>(null);
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  useEffect(() => {
    if (initialDocId) {
      setSelectedDocId(initialDocId);
    }
  }, [initialDocId]);

  const { data: docsPage } = useQuery({
    queryKey: ['documents-for-chat'],
    queryFn: () => documentsApi.list({ page: 0, size: 100, status: 'COMPLETED' }),
  });

  const { data: convPage, isLoading: convLoading } = useQuery({
    queryKey: ['conversations'],
    queryFn: () => conversationsApi.list(0, 40),
  });

  const { data: activeConv } = useQuery({
    queryKey: ['conversation', conversationId],
    queryFn: () => conversationsApi.get(conversationId!),
    enabled: Boolean(conversationId),
  });

  const { data: messagesPage, isLoading: messagesLoading } = useQuery({
    queryKey: ['messages', conversationId],
    queryFn: () => conversationsApi.messages(conversationId!, 0, 100),
    enabled: Boolean(conversationId),
  });

  useEffect(() => {
    if (activeConv?.documentId) {
      setSelectedDocId(activeConv.documentId);
    }
  }, [activeConv]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messagesPage?.content?.length]);

  const askMutation = useMutation({
    mutationFn: async (promptText: string) => {
      const payload = {
        question: promptText,
        documentId: selectedDocId || null,
        topK,
        similarityThreshold,
        temperature,
      };
      if (conversationId) {
        return ragApi.chatInConversation(conversationId, payload);
      }
      return ragApi.ask(payload);
    },
    onSuccess: (res) => {
      setChatError(null);
      setQuestion('');
      queryClient.invalidateQueries({ queryKey: ['conversations'] });
      queryClient.invalidateQueries({ queryKey: ['messages', res.conversationId] });
      if (!conversationId) {
        navigate(`/chat/${res.conversationId}`, { replace: true });
      }
    },
    onError: (err) => {
      setChatError(extractApiErrorMessage(err, 'Failed to generate grounded response.'));
    },
  });

  const renameMutation = useMutation({
    mutationFn: ({ id, title }: { id: string; title: string }) => conversationsApi.rename(id, title),
    onSuccess: () => {
      setRenameTarget(null);
      queryClient.invalidateQueries({ queryKey: ['conversations'] });
      if (conversationId) {
        queryClient.invalidateQueries({ queryKey: ['conversation', conversationId] });
      }
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => conversationsApi.remove(id),
    onSuccess: (_, deletedId) => {
      queryClient.invalidateQueries({ queryKey: ['conversations'] });
      if (conversationId === deletedId) {
        navigate('/chat');
      }
    },
  });

  const handleSend = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = question.trim();
    if (!trimmed || askMutation.isPending) return;
    setChatError(null);
    askMutation.mutate(trimmed);
  };

  const completedDocs = docsPage?.content ?? [];
  const conversations = convPage?.content ?? [];
  const messages = messagesPage?.content ?? [];

  return (
    <Box
      sx={{
        display: 'grid',
        gridTemplateColumns: { xs: '1fr', md: '290px 1fr' },
        gap: 2,
        height: { md: 'calc(100vh - 112px)' },
      }}
    >
      {/* Conversation History Sidebar */}
      <Card sx={{ display: 'flex', flexDirection: 'column', overflow: 'hidden', maxHeight: { xs: 280, md: '100%' } }}>
        <Box sx={{ p: 2, borderBottom: 1, borderColor: 'divider' }}>
          <Button
            variant="contained"
            fullWidth
            startIcon={<AddCommentOutlinedIcon />}
            onClick={() => {
              setChatError(null);
              navigate('/chat');
            }}
          >
            New Conversation
          </Button>
        </Box>
        <Box sx={{ overflowY: 'auto', flexGrow: 1 }}>
          {convLoading ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 3 }}>
              <CircularProgress size={24} />
            </Box>
          ) : conversations.length === 0 ? (
            <Typography variant="body2" color="text.secondary" sx={{ p: 2 }}>
              No conversations yet. Ask a question to start one.
            </Typography>
          ) : (
            <List dense disablePadding>
              {conversations.map((conv) => (
                <ListItemButton
                  key={conv.id}
                  selected={conv.id === conversationId}
                  onClick={() => navigate(`/chat/${conv.id}`)}
                  sx={{ py: 1.2, pr: 1, borderBottom: 1, borderColor: 'divider' }}
                >
                  <ListItemText
                    primary={conv.title}
                    secondary={conv.documentName ?? 'All indexed documents'}
                    primaryTypographyProps={{ noWrap: true, fontWeight: 600, fontSize: 13 }}
                    secondaryTypographyProps={{ noWrap: true, fontSize: 11 }}
                  />
                  <Stack direction="row" spacing={0.25} onClick={(e) => e.stopPropagation()}>
                    <IconButton
                      size="small"
                      onClick={() => {
                        setRenameTarget(conv);
                        setRenameTitle(conv.title);
                      }}
                    >
                      <EditOutlinedIcon fontSize="inherit" />
                    </IconButton>
                    <IconButton size="small" color="error" onClick={() => deleteMutation.mutate(conv.id)}>
                      <DeleteOutlineIcon fontSize="inherit" />
                    </IconButton>
                  </Stack>
                </ListItemButton>
              ))}
            </List>
          )}
        </Box>
      </Card>

      {/* Chat Workspace */}
      <Card sx={{ display: 'flex', flexDirection: 'column', overflow: 'hidden', minHeight: 500 }}>
        {/* Header & Document Scope Controls */}
        <Box sx={{ px: 2.5, py: 1.5, borderBottom: 1, borderColor: 'divider' }}>
          <Stack direction={{ xs: 'column', sm: 'row' }} alignItems={{ sm: 'center' }} justifyContent="space-between" gap={1.5}>
            <Box>
              <Typography variant="subtitle1" fontWeight={700}>
                {activeConv ? activeConv.title : 'Grounded RAG Assistant'}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                Answers are strictly grounded in your uploaded and indexed documents with traceable citations.
              </Typography>
            </Box>
            <Stack direction="row" spacing={1} alignItems="center">
              <FormControl size="small" sx={{ minWidth: 210 }}>
                <InputLabel>Document Scope</InputLabel>
                <Select
                  label="Document Scope"
                  value={selectedDocId}
                  onChange={(e) => setSelectedDocId(e.target.value)}
                >
                  <MenuItem value="">All Completed Documents</MenuItem>
                  {completedDocs.map((doc) => (
                    <MenuItem key={doc.id} value={doc.id}>
                      {doc.originalFilename}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
              <Tooltip title="Retrieval tuning">
                <IconButton onClick={() => setShowTuning((v) => !v)} color={showTuning ? 'primary' : 'default'}>
                  <TuneOutlinedIcon />
                </IconButton>
              </Tooltip>
            </Stack>
          </Stack>

          {showTuning && (
            <Box sx={{ mt: 2, pt: 2, borderTop: 1, borderColor: 'divider' }}>
              <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(3, 1fr)' }, gap: 3 }}>
                <Box>
                  <Typography variant="caption" fontWeight={600}>
                    Top-K Chunks: {topK}
                  </Typography>
                  <Slider size="small" min={1} max={15} value={topK} onChange={(_, v) => setTopK(v as number)} />
                </Box>
                <Box>
                  <Typography variant="caption" fontWeight={600}>
                    Similarity Threshold: {similarityThreshold.toFixed(2)}
                  </Typography>
                  <Slider
                    size="small"
                    min={0.1}
                    max={0.95}
                    step={0.05}
                    value={similarityThreshold}
                    onChange={(_, v) => setSimilarityThreshold(v as number)}
                  />
                </Box>
                <Box>
                  <Typography variant="caption" fontWeight={600}>
                    Temperature: {temperature.toFixed(1)}
                  </Typography>
                  <Slider
                    size="small"
                    min={0}
                    max={1}
                    step={0.1}
                    value={temperature}
                    onChange={(_, v) => setTemperature(v as number)}
                  />
                </Box>
              </Box>
            </Box>
          )}
        </Box>

        {/* Message List */}
        <Box sx={{ flexGrow: 1, overflowY: 'auto', p: 2.5, bgcolor: 'background.default' }}>
          {messagesLoading ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
              <CircularProgress />
            </Box>
          ) : messages.length === 0 ? (
            <Stack alignItems="center" justifyContent="center" sx={{ height: '100%', py: 6 }} spacing={2}>
              <AutoAwesomeIcon color="primary" sx={{ fontSize: 48 }} />
              <Typography variant="h6">Ask a question about your documents</Typography>
              <Typography variant="body2" color="text.secondary" textAlign="center" maxWidth={480}>
                Select a specific document or search across your entire library. Every answer returns
                verifiable document names, page numbers, and extracted excerpts.
              </Typography>
              {completedDocs.length === 0 && (
                <Alert severity="info">
                  You don&apos;t have any completed documents yet.{' '}
                  <Button size="small" onClick={() => navigate('/documents')}>
                    Upload a Document
                  </Button>
                </Alert>
              )}
            </Stack>
          ) : (
            <Stack spacing={2.5}>
              {messages.map((msg) => {
                const isUser = msg.role === 'USER';
                return (
                  <Box
                    key={msg.id}
                    sx={{
                      display: 'flex',
                      justifyContent: isUser ? 'flex-end' : 'flex-start',
                    }}
                  >
                    <Paper
                      elevation={0}
                      sx={{
                        p: 2,
                        maxWidth: { xs: '95%', md: '82%' },
                        bgcolor: isUser ? 'primary.main' : 'background.paper',
                        color: isUser ? '#fff' : 'text.primary',
                        border: isUser ? 'none' : 1,
                        borderColor: 'divider',
                        borderRadius: 2.5,
                      }}
                    >
                      {!isUser && (
                        <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }} flexWrap="wrap" useFlexGap>
                          <Chip
                            size="small"
                            icon={<VerifiedOutlinedIcon />}
                            label={msg.citations.length > 0 ? 'Grounded Answer' : 'Insufficient Context'}
                            color={msg.citations.length > 0 ? 'success' : 'warning'}
                            variant="outlined"
                          />
                          {msg.processingTimeMs !== null && (
                            <Chip size="small" label={`${msg.processingTimeMs} ms`} variant="outlined" />
                          )}
                          {msg.tokenUsage?.totalTokens && (
                            <Chip size="small" label={`${msg.tokenUsage.totalTokens} tokens`} variant="outlined" />
                          )}
                        </Stack>
                      )}

                      <Box sx={{ '& p': { m: 0, mb: 1, '&:last-child': { mb: 0 } }, fontSize: 14, lineHeight: 1.65 }}>
                        {isUser ? (
                          <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
                            {msg.content}
                          </Typography>
                        ) : (
                          <ReactMarkdown remarkPlugins={[remarkGfm]}>{msg.content}</ReactMarkdown>
                        )}
                      </Box>

                      {!isUser && msg.citations.length > 0 && (
                        <>
                          <Divider sx={{ my: 1.5 }} />
                          <CitationCards sources={msg.citations} />
                        </>
                      )}
                    </Paper>
                  </Box>
                );
              })}
              {askMutation.isPending && (
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, pl: 1 }}>
                  <CircularProgress size={18} />
                  <Typography variant="body2" color="text.secondary">
                    Retrieving document chunks and generating grounded response...
                  </Typography>
                </Box>
              )}
              <div ref={messagesEndRef} />
            </Stack>
          )}
        </Box>

        {chatError && (
          <Alert severity="error" sx={{ mx: 2, mt: 1 }} onClose={() => setChatError(null)}>
            {chatError}
          </Alert>
        )}

        {/* Composer */}
        <Box
          component="form"
          onSubmit={handleSend}
          sx={{ p: 2, borderTop: 1, borderColor: 'divider', bgcolor: 'background.paper' }}
        >
          <Stack direction="row" spacing={1.5} alignItems="flex-end">
            <TextField
              fullWidth
              multiline
              maxRows={4}
              size="small"
              placeholder="Ask a question about your uploaded documents..."
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault();
                  handleSend(e);
                }
              }}
            />
            <Button
              type="submit"
              variant="contained"
              endIcon={<SendIcon />}
              disabled={!question.trim() || askMutation.isPending}
              sx={{ px: 3, height: 40 }}
            >
              Ask
            </Button>
          </Stack>
        </Box>
      </Card>

      <Dialog open={Boolean(renameTarget)} onClose={() => setRenameTarget(null)} maxWidth="xs" fullWidth>
        <DialogTitle>Rename Conversation</DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            fullWidth
            margin="dense"
            label="Conversation Title"
            value={renameTitle}
            onChange={(e) => setRenameTitle(e.target.value)}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRenameTarget(null)}>Cancel</Button>
          <Button
            variant="contained"
            disabled={!renameTitle.trim() || renameMutation.isPending}
            onClick={() =>
              renameTarget && renameMutation.mutate({ id: renameTarget.id, title: renameTitle.trim() })
            }
          >
            Save
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
