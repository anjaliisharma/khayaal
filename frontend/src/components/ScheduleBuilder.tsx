import type { ScheduleType } from './scheduleTypes';
import { inputClass } from './FormField';
import { scheduleSummary } from './scheduleSummary';
export interface ScheduleValue {
  type: ScheduleType;
  localTimes: string[];
  daysOfWeek: string[];
  intervalDays: number | null;
  anchorDate: string | null;
}
const days = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
export function ScheduleBuilder({
  value,
  onChange
}: {
  value: ScheduleValue;
  onChange: (value: ScheduleValue) => void;
}) {
  const set = (patch: Partial<ScheduleValue>) => onChange({ ...value, ...patch });
  return (
    <fieldset className="space-y-5 rounded-2xl border p-5">
      <legend className="px-2 text-lg font-bold">Schedule</legend>
      <label className="block font-medium">
        Pattern
        <select
          className={`${inputClass} mt-2`}
          value={value.type}
          onChange={(e) => set({ type: e.target.value as ScheduleType })}
        >
          <option value="DAILY">Every day</option>
          <option value="WEEKDAYS">Selected days</option>
          <option value="EVERY_N_DAYS">Every few days</option>
        </select>
      </label>
      {value.type === 'WEEKDAYS' && (
        <div>
          <span className="font-medium">Days</span>
          <div className="mt-2 flex flex-wrap gap-2">
            {days.map((day) => (
              <label key={day} className="flex min-h-11 items-center gap-2 rounded-xl border px-3">
                <input
                  type="checkbox"
                  checked={value.daysOfWeek.includes(day)}
                  onChange={(e) =>
                    set({
                      daysOfWeek: e.target.checked
                        ? [...value.daysOfWeek, day]
                        : value.daysOfWeek.filter((d) => d !== day)
                    })
                  }
                />
                {day.slice(0, 3)}
              </label>
            ))}
          </div>
        </div>
      )}
      {value.type === 'EVERY_N_DAYS' && (
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="font-medium">
            Every N days
            <input
              className={`${inputClass} mt-2`}
              type="number"
              min="2"
              value={value.intervalDays ?? 2}
              onChange={(e) => set({ intervalDays: Number(e.target.value) })}
            />
          </label>
          <label className="font-medium">
            Starting on
            <input
              className={`${inputClass} mt-2`}
              type="date"
              value={value.anchorDate ?? ''}
              onChange={(e) => set({ anchorDate: e.target.value })}
            />
          </label>
        </div>
      )}
      <div>
        <span className="font-medium">Times</span>
        <div className="mt-2 grid gap-3 sm:grid-cols-3">
          {value.localTimes.map((time, index) => (
            <div className="flex gap-2" key={index}>
              <input
                aria-label={`Dose time ${index + 1}`}
                className={inputClass}
                type="time"
                value={time}
                onChange={(e) =>
                  set({
                    localTimes: value.localTimes.map((item, i) =>
                      i === index ? e.target.value : item
                    )
                  })
                }
              />
              {value.localTimes.length > 1 && (
                <button
                  type="button"
                  aria-label={`Remove time ${index + 1}`}
                  onClick={() =>
                    set({ localTimes: value.localTimes.filter((_, i) => i !== index) })
                  }
                >
                  ×
                </button>
              )}
            </div>
          ))}
        </div>
        <button
          type="button"
          className="mt-3 font-semibold text-teal-700"
          disabled={value.localTimes.length >= 6}
          onClick={() => set({ localTimes: [...value.localTimes, '20:00'] })}
        >
          + Add another time
        </button>
      </div>
      <p className="rounded-xl bg-teal-50 p-3 text-sm text-teal-900 dark:bg-teal-950 dark:text-teal-100">
        {scheduleSummary(value)}
      </p>
    </fieldset>
  );
}
