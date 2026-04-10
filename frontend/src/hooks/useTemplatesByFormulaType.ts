import { useEffect, useState } from 'react';
import axios from 'axios';
import { useServerStore } from '../stores/serverStore';

/**
 * A row from FF_FORMULA_TEMPLATES_VL as seen by the editor's "Start with"
 * sample picker. Mirrors {@code TemplateService.rowToMap} on the Java side
 * but only the fields the consumer view actually needs.
 */
export interface DbTemplate {
  template_id?: number;
  template_code?: string;
  name: string;
  description: string;
  code: string;
  rule?: string;
  source_type?: string;
  active_flag?: string;
  semantic_flag?: string;
  sort_order?: number;
}

/**
 * Fetches the active templates for a given formula type from the database
 * via {@code GET /templates?formula_type=...}. This is the canonical source
 * for the main editor's "Start with" sample dropdown and for the Manage
 * Templates panel.
 *
 * Pass {@code null} or empty string to clear the result.
 */
export function useTemplatesByFormulaType(formulaType: string | null | undefined) {
  const { current } = useServerStore();
  const [templates, setTemplates] = useState<DbTemplate[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!formulaType) {
      setTemplates([]);
      setError(null);
      return;
    }

    let cancelled = false;
    setLoading(true);
    setError(null);

    const headers: Record<string, string> = {};
    if (current.auth) {
      headers['Authorization'] = `Basic ${btoa(
        `${current.auth.username}:${current.auth.password}`
      )}`;
    }

    axios
      .get<DbTemplate[]>(`${current.baseUrl}${current.apiPrefix}/templates`, {
        // Active-only — admins manage inactive rows in the Templates UI;
        // the consumer-facing dropdown should hide them.
        params: { formula_type: formulaType },
        headers,
      })
      .then((resp) => {
        if (!cancelled) {
          setTemplates(resp.data || []);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setError(err?.response?.data?.error || err.message || 'Failed to load templates');
          setTemplates([]);
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [formulaType, current.baseUrl, current.apiPrefix, current.auth]);

  return { templates, loading, error };
}
