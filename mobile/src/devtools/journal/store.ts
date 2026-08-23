/**
 * Journal de scénarios — `docs/10-validation-mode.md` §9.
 *
 * Consignation manuelle des observations faites contre le backend, avec export
 * Markdown. L'export alimente directement le journal de compatibilité de
 * `docs/09-api-evolution.md` §7 et remonte au backend sous forme d'issue.
 *
 * **La persistance n'est pas un détail.** Une observation se fait souvent juste
 * après une coupure réseau volontaire, parfois après un rechargement complet de
 * l'app : un journal en mémoire perdrait exactement ce qu'il sert à retenir.
 */

import { create } from 'zustand';

import { KvKey, kvGetJson, kvSetJson } from '@/lib/storage/kv';

/** Verdict d'un scénario — repris tel quel dans l'export. */
export type JournalStatus = 'expected' | 'unexpected' | 'open';

export const STATUS_LABELS: Record<JournalStatus, string> = {
  expected: '✅ conforme à l’attendu',
  unexpected: '❌ écart constaté',
  open: '⏳ à confirmer',
};

export interface JournalEntry {
  id: string;
  /** Instant ISO-8601 — sérialisable tel quel, contrairement à une `Date`. */
  at: string;
  /** Une ligne : « Transfert 25 000 XOF, réseau coupé à 800 ms ». */
  title: string;
  /** Numéro du scénario de `docs/10-validation-mode.md` §11, si applicable. */
  scenario: number | null;
  correlationId: string | null;
  observed: string;
  conclusion: string;
  status: JournalStatus;
}

export type JournalDraft = Omit<JournalEntry, 'id' | 'at'>;

interface JournalState {
  entries: JournalEntry[];
  add: (draft: JournalDraft) => void;
  update: (id: string, patch: Partial<JournalDraft>) => void;
  remove: (id: string) => void;
  clear: () => void;
}

/**
 * Le stockage est chargé paresseusement : MMKV est un module natif, et les
 * tests unitaires du module pur d'export ne doivent pas l'exiger.
 */
function load(): JournalEntry[] {
  try {
    return kvGetJson<JournalEntry[]>(KvKey.devtoolsJournal, []);
  } catch {
    return [];
  }
}

function persist(entries: JournalEntry[]): void {
  try {
    kvSetJson(KvKey.devtoolsJournal, entries);
  } catch {
    // Un journal non persisté vaut mieux qu'un panneau qui plante.
  }
}

let sequence = 0;

function newId(): string {
  sequence += 1;
  return `${Date.now().toString(36)}-${sequence.toString(36)}`;
}

export const useJournal = create<JournalState>((set, get) => ({
  entries: load(),

  add: (draft) => {
    // Le plus récent en tête : c'est l'ordre dans lequel on relit un journal.
    const entries = [{ ...draft, id: newId(), at: new Date().toISOString() }, ...get().entries];
    persist(entries);
    set({ entries });
  },

  update: (id, patch) => {
    const entries = get().entries.map((entry) =>
      entry.id === id ? { ...entry, ...patch } : entry,
    );
    persist(entries);
    set({ entries });
  },

  remove: (id) => {
    const entries = get().entries.filter((entry) => entry.id !== id);
    persist(entries);
    set({ entries });
  },

  clear: () => {
    persist([]);
    set({ entries: [] });
  },
}));

// ────────────────────────────────────────────────────────── Export Markdown ─

function formatStamp(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  const pad = (value: number) => String(value).padStart(2, '0');
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ` +
    `${pad(date.getHours())}:${pad(date.getMinutes())}`
  );
}

/** Un texte multiligne reste lisible en tableau s'il n'y coupe pas les cellules. */
function indent(text: string): string {
  return text
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.length > 0)
    .join('\n  ');
}

/**
 * Export Markdown — module **pur**, testable sans store ni couche native.
 *
 * Le format suit celui de `docs/09-api-evolution.md` §7 : un tableau de synthèse
 * suivi du détail. Le tableau seul suffit à ouvrir une issue backend ; le détail
 * porte ce qui a été réellement observé.
 */
export function toMarkdown(entries: readonly JournalEntry[], generatedAt: Date = new Date()): string {
  const lines: string[] = [
    '# Journal de validation Kora',
    '',
    `Export du ${formatStamp(generatedAt.toISOString())} — ${entries.length} observation${
      entries.length > 1 ? 's' : ''
    }.`,
    '',
  ];

  if (entries.length === 0) {
    lines.push('_Aucune observation consignée._', '');
    return lines.join('\n');
  }

  lines.push(
    '| Date | Scénario | Observation | Statut |',
    '|---|---|---|---|',
    ...entries.map(
      (entry) =>
        `| ${formatStamp(entry.at)} | ${entry.scenario === null ? '—' : `#${entry.scenario}`} | ${
          entry.title
        } | ${STATUS_LABELS[entry.status]} |`,
    ),
    '',
    '---',
    '',
  );

  for (const entry of entries) {
    lines.push(`## [${formatStamp(entry.at)}] ${entry.title}`, '');
    if (entry.scenario !== null) {
      lines.push(`- **Scénario** — §11 #${entry.scenario}`);
    }
    if (entry.correlationId) {
      lines.push(`- **Corrélation** — \`${entry.correlationId}\``);
    }
    lines.push(
      `- **Observé** — ${indent(entry.observed) || '—'}`,
      `- **Conclusion** — ${indent(entry.conclusion) || '—'}`,
      `- **Statut** — ${STATUS_LABELS[entry.status]}`,
      '',
    );
  }

  return lines.join('\n');
}
