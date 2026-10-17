import type { components } from './schema';

type Schema = components['schemas'];

export type HouseholdRole = NonNullable<Schema['HouseholdView']['role']>;
export type TokenResponse = Required<Schema['TokenResponse']>;
export type Household = Required<Schema['HouseholdView']>;

export type Recipient = Omit<
  Required<Schema['RecipientView']>,
  'birthYear' | 'notes' | 'linkedUserId'
> & {
  birthYear: number | null;
  notes: string | null;
  linkedUserId: string | null;
};

export type Medicine = Omit<
  Required<Schema['MedicineView']>,
  'strength' | 'instructions' | 'endDate'
> & {
  strength: string | null;
  instructions: string | null;
  endDate: string | null;
};

export type TodayDose = Omit<
  Required<Schema['TodayDose']>,
  'status' | 'actedBy' | 'actedByName' | 'strength'
> & {
  status: 'PENDING' | 'TAKEN' | 'SKIPPED' | 'MISSED';
  actedBy: string | null;
  actedByName: string | null;
  strength: string | null;
};

export type PreviewOccurrence = Required<Schema['PreviewOccurrence']>;
export interface ProblemDetail {
  title?: string;
  detail?: string;
  status?: number;
}
