import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { useAuth } from '../auth/authContext';
import { Button } from '../components/ui/button';
import { FormField, inputClass } from '../components/FormField';

const loginSchema = z.object({ email: z.string().email(), password: z.string().min(1) });
const registerSchema = loginSchema.extend({
  displayName: z.string().min(1).max(100),
  password: z.string().min(10)
});
type AuthValues = z.infer<typeof registerSchema>;
export function AuthPage({ mode }: { mode: 'login' | 'register' }) {
  const auth = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const schema = mode === 'login' ? loginSchema : registerSchema;
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting }
  } = useForm<AuthValues>({
    resolver: zodResolver(schema),
    defaultValues: { email: '', password: '', displayName: '' }
  });
  if (auth.authenticated) return <Navigate to="/" replace />;
  const submit = handleSubmit(async (values) => {
    try {
      if (mode === 'login') await auth.login(values.email, values.password);
      else await auth.register(values.displayName, values.email, values.password);
      navigate((location.state as { from?: string } | null)?.from ?? '/');
    } catch (error) {
      setError('root', { message: error instanceof Error ? error.message : 'Unable to continue' });
    }
  });
  return (
    <main className="grid min-h-screen place-items-center bg-canvas p-5">
      <section className="w-full max-w-md rounded-3xl border bg-surface p-7 shadow-sm">
        <div className="mb-7 flex items-center gap-3">
          <img src="/khayaal-icon.svg" alt="" className="h-12 w-12" />
          <div>
            <h1 className="text-2xl font-bold">
              {mode === 'login' ? 'Welcome back' : 'Create your Khayaal account'}
            </h1>
            <p className="text-sm text-muted">Care, remembered.</p>
          </div>
        </div>
        <form className="space-y-5" onSubmit={submit}>
          {mode === 'register' && (
            <FormField label="Your name" error={errors.displayName?.message}>
              <input className={inputClass} autoComplete="name" {...register('displayName')} />
            </FormField>
          )}
          <FormField label="Email" error={errors.email?.message}>
            <input
              className={inputClass}
              type="email"
              autoComplete="email"
              {...register('email')}
            />
          </FormField>
          <FormField label="Password" error={errors.password?.message}>
            <input
              className={inputClass}
              type="password"
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              {...register('password')}
            />
          </FormField>
          {errors.root && (
            <p role="alert" className="text-red-700">
              {errors.root.message}
            </p>
          )}
          <Button className="w-full" disabled={isSubmitting}>
            {isSubmitting ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}
          </Button>
        </form>
        <p className="mt-6 text-center text-sm">
          {mode === 'login' ? (
            <>
              New to Khayaal?{' '}
              <Link className="font-semibold text-teal-700" to="/register">
                Create an account
              </Link>
            </>
          ) : (
            <>
              Already registered?{' '}
              <Link className="font-semibold text-teal-700" to="/login">
                Sign in
              </Link>
            </>
          )}
        </p>
        <p className="mt-7 border-t pt-5 text-xs text-muted">
          Khayaal helps you remember and track. It is not medical advice. Follow your doctor's and
          pharmacist's instructions.
        </p>
      </section>
    </main>
  );
}
