import React, { useMemo } from 'react';
import katex from 'katex';

interface MathMarkdownProps {
  text: string;
}

function renderLatex(tex: string, displayMode: boolean): string {
  try {
    return katex.renderToString(tex.trim(), {
      displayMode,
      throwOnError: false,
      strict: false,
      trust: false
    });
  } catch {
    return tex;
  }
}

interface TextSegment {
  type: 'text' | 'inline-math' | 'block-math' | 'code-block';
  value: string;
  lang?: string;
}

function splitMarkdownSegments(raw: string): TextSegment[] {
  // Strip raw ```json ... ``` drawing plan blocks so user sees clean math explanation
  const cleaned = raw.replace(/```json[\s\S]*?```/gi, '').trim();
  const segments: TextSegment[] = [];

  const tokenRegex =
    /(```[\w-]*\n[\s\S]*?```|\$\$[\s\S]+?\$\$|\\\[[\s\S]+?\\\]|\\\([\s\S]+?\\\)|\$(?!\s)(?:\\.|[^$\\])+\$)/g;

  let lastIndex = 0;
  let match: RegExpExecArray | null;
  while ((match = tokenRegex.exec(cleaned)) !== null) {
    if (match.index > lastIndex) {
      segments.push({ type: 'text', value: cleaned.slice(lastIndex, match.index) });
    }
    const token = match[0];
    if (token.startsWith('```')) {
      const firstNewline = token.indexOf('\n');
      const lang = token.slice(3, firstNewline).trim();
      const body = token.slice(firstNewline + 1, -3);
      segments.push({ type: 'code-block', value: body, lang });
    } else if (token.startsWith('$$') && token.endsWith('$$')) {
      segments.push({ type: 'block-math', value: token.slice(2, -2) });
    } else if (token.startsWith('\\[') && token.endsWith('\\]')) {
      segments.push({ type: 'block-math', value: token.slice(2, -2) });
    } else if (token.startsWith('\\(') && token.endsWith('\\)')) {
      segments.push({ type: 'inline-math', value: token.slice(2, -2) });
    } else if (token.startsWith('$') && token.endsWith('$')) {
      segments.push({ type: 'inline-math', value: token.slice(1, -1) });
    }
    lastIndex = tokenRegex.lastIndex;
  }
  if (lastIndex < cleaned.length) {
    segments.push({ type: 'text', value: cleaned.slice(lastIndex) });
  }
  return segments;
}

function renderInlineFormattedText(text: string, keyPrefix: string): React.ReactNode[] {
  const nodes: React.ReactNode[] = [];
  const lines = text.split('\n');
  lines.forEach((line, lineIdx) => {
    const trimmed = line.trimStart();
    let content: React.ReactNode = null;

    const headingMatch = trimmed.match(/^(#{1,3})\s+(.*)$/);
    const bulletMatch = trimmed.match(/^[-•*]\s+(.*)$/);

    const formatSpans = (s: string): React.ReactNode[] => {
      const parts: React.ReactNode[] = [];
      const re = /(\*\*[^*]+\*\*|`[^`]+`)/g;
      let last = 0;
      let m: RegExpExecArray | null;
      let idx = 0;
      while ((m = re.exec(s)) !== null) {
        if (m.index > last) parts.push(s.slice(last, m.index));
        const tok = m[0];
        if (tok.startsWith('**') && tok.endsWith('**')) {
          parts.push(<strong key={`${keyPrefix}_${lineIdx}_b_${idx++}`}>{tok.slice(2, -2)}</strong>);
        } else if (tok.startsWith('`') && tok.endsWith('`')) {
          parts.push(
            <code
              key={`${keyPrefix}_${lineIdx}_c_${idx++}`}
              className="mono"
              style={{
                background: 'rgba(148,163,184,0.18)',
                padding: '1px 5px',
                borderRadius: 6,
                fontSize: '0.9em'
              }}
            >
              {tok.slice(1, -1)}
            </code>
          );
        }
        last = re.lastIndex;
      }
      if (last < s.length) parts.push(s.slice(last));
      return parts;
    };

    if (headingMatch) {
      const level = headingMatch[1].length;
      content = (
        <div
          key={`${keyPrefix}_h_${lineIdx}`}
          style={{
            fontWeight: 800,
            fontSize: level === 1 ? 15.5 : level === 2 ? 14.5 : 13.5,
            color: 'var(--accent)',
            margin: '8px 0 4px'
          }}
        >
          {formatSpans(headingMatch[2])}
        </div>
      );
    } else if (bulletMatch) {
      content = (
        <div key={`${keyPrefix}_li_${lineIdx}`} style={{ display: 'flex', gap: 6, paddingLeft: 4, margin: '2px 0' }}>
          <span style={{ color: 'var(--accent)', fontWeight: 800 }}>•</span>
          <span>{formatSpans(bulletMatch[1])}</span>
        </div>
      );
    } else {
      content = (
        <React.Fragment key={`${keyPrefix}_ln_${lineIdx}`}>
          {formatSpans(line)}
          {lineIdx < lines.length - 1 ? <br /> : null}
        </React.Fragment>
      );
    }
    nodes.push(content);
  });
  return nodes;
}

export const MathMarkdown: React.FC<MathMarkdownProps> = ({ text }) => {
  const segments = useMemo(() => splitMarkdownSegments(text), [text]);

  return (
    <div className="math-markdown" style={{ fontSize: 13.5, lineHeight: 1.65, wordBreak: 'break-word' }}>
      {segments.map((seg, idx) => {
        if (seg.type === 'block-math') {
          const html = renderLatex(seg.value, true);
          return (
            <div
              key={idx}
              className="math-block-scroll"
              style={{ margin: '8px 0', overflowX: 'auto', padding: '4px 2px' }}
              dangerouslySetInnerHTML={{ __html: html }}
            />
          );
        }
        if (seg.type === 'inline-math') {
          const html = renderLatex(seg.value, false);
          return <span key={idx} dangerouslySetInnerHTML={{ __html: html }} />;
        }
        if (seg.type === 'code-block') {
          return (
            <pre
              key={idx}
              className="mono"
              style={{
                margin: '8px 0',
                padding: '10px 12px',
                borderRadius: 10,
                background: 'rgba(2,6,23,0.45)',
                border: '1px solid var(--border-subtle)',
                overflowX: 'auto',
                fontSize: 12
              }}
            >
              {seg.value}
            </pre>
          );
        }
        return <React.Fragment key={idx}>{renderInlineFormattedText(seg.value, `s${idx}`)}</React.Fragment>;
      })}
    </div>
  );
};
