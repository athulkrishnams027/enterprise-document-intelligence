import {
  Box,
  Button,
  Card,
  CardActionArea,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Stack,
  Typography,
} from '@mui/material';
import ArticleOutlinedIcon from '@mui/icons-material/ArticleOutlined';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { SourceCitation } from '../types/api';

function formatLocation(citation: SourceCitation): string {
  if (citation.pageNumber !== null && citation.pageNumber !== undefined) {
    return `Page ${citation.pageNumber}`;
  }
  if (citation.sectionTitle) {
    return citation.sectionTitle;
  }
  return 'Document text';
}

export function CitationCards({ sources }: { sources: SourceCitation[] }) {
  const [selected, setSelected] = useState<SourceCitation | null>(null);
  const navigate = useNavigate();

  if (!sources || sources.length === 0) {
    return null;
  }

  return (
    <Box sx={{ mt: 1.5 }}>
      <Typography variant="caption" fontWeight={700} color="text.secondary" sx={{ display: 'block', mb: 1 }}>
        VERIFIED SOURCES ({sources.length})
      </Typography>
      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: { xs: '1fr', sm: 'repeat(auto-fill, minmax(230px, 1fr))' },
          gap: 1,
        }}
      >
        {sources.map((src, idx) => (
          <Card key={`${src.chunkId}-${idx}`} variant="outlined" sx={{ bgcolor: 'background.default' }}>
            <CardActionArea onClick={() => setSelected(src)}>
              <CardContent sx={{ p: 1.5, '&:last-child': { pb: 1.5 } }}>
                <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between">
                  <Stack direction="row" spacing={0.75} alignItems="center" sx={{ minWidth: 0 }}>
                    <ArticleOutlinedIcon fontSize="small" color="primary" />
                    <Typography variant="caption" fontWeight={700} noWrap>
                      [Source {idx + 1}] {src.documentName}
                    </Typography>
                  </Stack>
                </Stack>
                <Stack direction="row" spacing={0.75} sx={{ mt: 0.75 }} flexWrap="wrap" useFlexGap>
                  <Chip size="small" label={formatLocation(src)} color="primary" variant="outlined" />
                  {src.relevanceScore !== null && (
                    <Chip size="small" label={`${Math.round(src.relevanceScore * 100)}% match`} />
                  )}
                </Stack>
                <Typography
                  variant="caption"
                  color="text.secondary"
                  sx={{
                    mt: 0.75,
                    display: '-webkit-box',
                    WebkitLineClamp: 2,
                    WebkitBoxOrient: 'vertical',
                    overflow: 'hidden',
                  }}
                >
                  {src.excerpt}
                </Typography>
              </CardContent>
            </CardActionArea>
          </Card>
        ))}
      </Box>

      <Dialog open={Boolean(selected)} onClose={() => setSelected(null)} maxWidth="sm" fullWidth>
        {selected && (
          <>
            <DialogTitle sx={{ fontWeight: 700 }}>
              Source Citation: {selected.documentName}
            </DialogTitle>
            <DialogContent dividers>
              <Stack direction="row" spacing={1} sx={{ mb: 2 }} flexWrap="wrap" useFlexGap>
                <Chip label={formatLocation(selected)} color="primary" size="small" />
                <Chip label={`Chunk: ${selected.chunkId}`} size="small" variant="outlined" />
                {selected.relevanceScore !== null && (
                  <Chip label={`Relevance: ${(selected.relevanceScore * 100).toFixed(1)}%`} size="small" />
                )}
              </Stack>
              <Divider sx={{ mb: 2 }} />
              <Typography variant="subtitle2" fontWeight={700} gutterBottom>
                Extracted Passage
              </Typography>
              <Box
                sx={{
                  p: 2,
                  borderRadius: 2,
                  bgcolor: 'action.hover',
                  whiteSpace: 'pre-wrap',
                  fontSize: 14,
                  lineHeight: 1.6,
                }}
              >
                {selected.excerpt}
              </Box>
            </DialogContent>
            <DialogActions>
              {selected.documentId && (
                <Button
                  startIcon={<OpenInNewIcon />}
                  onClick={() => {
                    const docId = selected.documentId;
                    setSelected(null);
                    navigate(`/documents/${docId}`);
                  }}
                >
                  Open Document Details
                </Button>
              )}
              <Button variant="contained" onClick={() => setSelected(null)}>
                Close
              </Button>
            </DialogActions>
          </>
        )}
      </Dialog>
    </Box>
  );
}
