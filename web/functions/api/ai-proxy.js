/**
 * Cloudflare Pages Function: /api/ai-proxy
 * Proxies HTTPS requests to AI providers (Google AI Studio, OpenAI, OpenRouter, Groq,
 * DeepSeek, Anthropic Claude, or Custom OpenAI-compatible endpoints) with full SSE
 * streaming support and zero browser CORS issues.
 */

function isBlockedHost(hostname) {
  const host = hostname.toLowerCase();
  if (
    host === 'localhost' ||
    host === '0.0.0.0' ||
    host.endsWith('.local') ||
    host.endsWith('.internal') ||
    host === '::1' ||
    host.startsWith('[::1]')
  ) {
    return true;
  }
  const ipv4Match = host.match(/^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/);
  if (ipv4Match) {
    const [a, b] = ipv4Match.slice(1).map(Number);
    if (
      a === 10 ||
      a === 127 ||
      a === 0 ||
      (a === 169 && b === 254) ||
      (a === 172 && b >= 16 && b <= 31) ||
      (a === 192 && b === 168)
    ) {
      return true;
    }
  }
  return false;
}

export async function onRequestOptions() {
  return new Response(null, {
    status: 204,
    headers: {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type'
    }
  });
}

export async function onRequestPost(context) {
  try {
    const payload = await context.request.json();
    const targetUrl = typeof payload?.targetUrl === 'string' ? payload.targetUrl.trim() : '';
    const method = (typeof payload?.method === 'string' ? payload.method : 'POST').toUpperCase();
    const rawHeaders = payload?.headers && typeof payload.headers === 'object' ? payload.headers : {};

    if (!targetUrl) {
      return new Response(JSON.stringify({ error: 'Thiếu targetUrl' }), {
        status: 400,
        headers: { 'Content-Type': 'application/json' }
      });
    }

    let parsed;
    try {
      parsed = new URL(targetUrl);
    } catch {
      return new Response(JSON.stringify({ error: 'Địa chỉ URL không hợp lệ' }), {
        status: 400,
        headers: { 'Content-Type': 'application/json' }
      });
    }

    if (parsed.protocol !== 'https:' || isBlockedHost(parsed.hostname)) {
      return new Response(JSON.stringify({ error: 'Chỉ hỗ trợ endpoint HTTPS công khai hợp lệ' }), {
        status: 403,
        headers: { 'Content-Type': 'application/json' }
      });
    }

    if (method !== 'POST' && method !== 'GET') {
      return new Response(JSON.stringify({ error: 'Phương thức không được hỗ trợ' }), {
        status: 405,
        headers: { 'Content-Type': 'application/json' }
      });
    }

    const forwardHeaders = new Headers();
    for (const [k, v] of Object.entries(rawHeaders)) {
      const lower = k.toLowerCase();
      if (lower === 'host' || lower === 'content-length' || lower === 'connection') continue;
      if (typeof v === 'string') forwardHeaders.set(k, v);
    }
    if (method === 'POST' && !forwardHeaders.has('Content-Type')) {
      forwardHeaders.set('Content-Type', 'application/json');
    }

    const init = {
      method,
      headers: forwardHeaders
    };
    if (method === 'POST' && payload.body !== undefined) {
      init.body = typeof payload.body === 'string' ? payload.body : JSON.stringify(payload.body);
    }

    const upstream = await fetch(parsed.toString(), init);

    const responseHeaders = new Headers();
    const ct = upstream.headers.get('Content-Type') || 'application/json; charset=utf-8';
    responseHeaders.set('Content-Type', ct);
    responseHeaders.set('Cache-Control', 'no-cache, no-transform');

    return new Response(upstream.body, {
      status: upstream.status,
      headers: responseHeaders
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Lỗi proxy không xác định';
    return new Response(JSON.stringify({ error: message }), {
      status: 500,
      headers: { 'Content-Type': 'application/json' }
    });
  }
}
