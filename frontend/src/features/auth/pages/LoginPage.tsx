import { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { User, Lock, Eye, EyeOff } from 'lucide-react';
import { useActor } from '@/app/providers/useActor';
import { isValidUuid } from '@/shared/lib/actor';

export function LoginPage() {
  const navigate = useNavigate();
  const { setActor } = useActor();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(false);
  const [loginNotice, setLoginNotice] = useState(false);

  const [usernameError, setUsernameError] = useState('');
  const [passwordError, setPasswordError] = useState('');

  const [devActorInput, setDevActorInput] = useState('');
  const [devActorError, setDevActorError] = useState('');

  const currentYear = new Date().getFullYear();

  const handleLoginSubmit = useCallback(
    (e: React.FormEvent) => {
      e.preventDefault();
      const uErr = !username.trim() ? 'Login kiritilishi shart' : '';
      const pErr = !password ? 'Parol kiritilishi shart' : '';
      setUsernameError(uErr);
      setPasswordError(pErr);
      if (uErr || pErr) return;
      setLoginNotice(true);
    },
    [username, password],
  );

  const handleDevActorSubmit = useCallback(
    (e: React.FormEvent) => {
      e.preventDefault();
      const trimmed = devActorInput.trim();
      if (!trimmed) {
        setDevActorError('UUID kiritilishi shart');
        return;
      }
      if (!isValidUuid(trimmed)) {
        setDevActorError("Noto'g'ri UUID formati");
        return;
      }
      setActor(trimmed);
      navigate('/crm/leads', { replace: true });
    },
    [devActorInput, setActor, navigate],
  );

  return (
    <div className="login-page">
      <div className="login-visual">
        <div className="login-visual__overlay" />
        <div className="login-visual__content">
          <img
            src="/branding/oxu-kids-logo.png"
            alt="Oxu Kids logo"
            className="login-visual__logo"
          />
          <h1 className="login-visual__title">OXU KIDS CRM</h1>
          <p className="login-visual__subtitle">e-bog'cha boshqaruv tizimi</p>
          <div className="login-visual__divider" />
          <p className="login-visual__description">
            Bolajonlar tarbiyasi va ta'lim jarayonini raqamli boshqarish uchun
            zamonaviy yechim.
          </p>
        </div>
        <div className="login-visual__footer">
          <p>&copy; {currentYear} Oxu Kids. Barcha huquqlar himoyalangan.</p>
        </div>
      </div>

      <div className="login-form-panel">
        <div className="login-card">
          <h2 className="login-card__title">Xush kelibsiz!</h2>
          <p className="login-card__subtitle">
            Hisobingizga kirish uchun ma'lumotlaringizni kiriting.
          </p>

          <form onSubmit={handleLoginSubmit} className="login-form">
            <div className="login-field">
              <label htmlFor="login-username" className="login-field__label">
                Login
              </label>
              <div className="login-field__input-wrapper">
                <User size={18} className="login-field__icon" aria-hidden="true" />
                <input
                  id="login-username"
                  type="text"
                  placeholder="Loginni kiriting"
                  value={username}
                  onChange={(e) => {
                    setUsername(e.target.value);
                    if (usernameError) setUsernameError('');
                  }}
                  className="login-field__input"
                  autoComplete="username"
                  aria-invalid={!!usernameError}
                  aria-describedby={usernameError ? 'login-username-error' : undefined}
                />
              </div>
              {usernameError && (
                <span id="login-username-error" className="login-field__error" role="alert">
                  {usernameError}
                </span>
              )}
            </div>

            <div className="login-field">
              <label htmlFor="login-password" className="login-field__label">
                Parol
              </label>
              <div className="login-field__input-wrapper">
                <Lock size={18} className="login-field__icon" aria-hidden="true" />
                <input
                  id="login-password"
                  type={showPassword ? 'text' : 'password'}
                  placeholder="Parolni kiriting"
                  value={password}
                  onChange={(e) => {
                    setPassword(e.target.value);
                    if (passwordError) setPasswordError('');
                  }}
                  className="login-field__input"
                  autoComplete="current-password"
                  aria-invalid={!!passwordError}
                  aria-describedby={passwordError ? 'login-password-error' : undefined}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="login-field__toggle"
                  aria-label={showPassword ? 'Parolni yashirish' : "Parolni ko'rsatish"}
                >
                  {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
              {passwordError && (
                <span id="login-password-error" className="login-field__error" role="alert">
                  {passwordError}
                </span>
              )}
            </div>

            <div className="login-options">
              <label className="login-checkbox">
                <input
                  type="checkbox"
                  checked={rememberMe}
                  onChange={(e) => setRememberMe(e.target.checked)}
                  className="login-checkbox__input"
                />
                <span className="login-checkbox__label">Meni eslab qol</span>
              </label>
              <span className="login-options__forgot">Parolni unutdingizmi?</span>
            </div>

            <button type="submit" className="login-submit">
              Tizimga kirish
            </button>

            {loginNotice && (
              <div className="login-notice" role="alert">
                Login tizimi backend autentifikatsiya bosqichidan so'ng faollashadi.
              </div>
            )}
          </form>

          {import.meta.env.DEV && (
            <div className="login-dev">
              <div className="login-dev__divider">
                <span>Dasturchi rejimi</span>
              </div>
              <p className="login-dev__description">
                Vaqtinchalik ruxsat berish mexanizmi. Haqiqiy tizimga kirish emas.
              </p>
              <form onSubmit={handleDevActorSubmit} className="login-dev__form">
                <div className="login-field">
                  <label htmlFor="dev-actor" className="login-field__label">
                    Foydalanuvchi UUID
                  </label>
                  <input
                    id="dev-actor"
                    type="text"
                    placeholder="44444444-4444-4444-8444-444444444444"
                    value={devActorInput}
                    onChange={(e) => {
                      setDevActorInput(e.target.value);
                      setDevActorError('');
                    }}
                    className="login-field__input login-field__input--dev"
                  />
                  {devActorError && (
                    <span className="login-field__error" role="alert">
                      {devActorError}
                    </span>
                  )}
                </div>
                <button type="submit" className="login-dev__submit">
                  Davom etish
                </button>
              </form>
            </div>
          )}

          <div className="login-card__footer">
            <p>&copy; Oxu Kids. Barcha huquqlar himoyalangan.</p>
          </div>
        </div>
      </div>
    </div>
  );
}
