import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';

// ── Loan product data ─────────────────────────────────────────────────────────
const LOAN_PRODUCTS = [
  {
    type: 'Personal Loan',
    tagline: 'Your goals, funded today.',
    rate: '8.5',
    maxAmount: '50,000',
    maxTerm: '5 years',
    color: '#1a56db',
    accent: '#e8f0fe',
    icon: '👤',
    features: ['Medical emergencies', 'Education fees', 'Home renovation', 'Holiday travel'],
    badge: 'Most Popular',
  },
  {
    type: 'Home Loan',
    tagline: 'Plant roots in paradise.',
    rate: '6.5',
    maxAmount: '500,000',
    maxTerm: '25 years',
    color: '#0e7c50',
    accent: '#e6f7f0',
    icon: '🏠',
    features: ['Buy your first home', 'Build new property', 'Refinance existing', 'Renovation finance'],
    badge: 'Best Rate',
  },
  {
    type: 'Vehicle Loan',
    tagline: 'Drive away this week.',
    rate: '7.5',
    maxAmount: '80,000',
    maxTerm: '7 years',
    color: '#b45309',
    accent: '#fef3e2',
    icon: '🚗',
    features: ['New vehicles', 'Used vehicles', 'Motorbikes', 'Commercial vehicles'],
    badge: 'Fast Approval',
  },
  {
    type: 'Business Loan',
    tagline: 'Grow without limits.',
    rate: '9.0',
    maxAmount: '200,000',
    maxTerm: '10 years',
    color: '#6d28d9',
    accent: '#f0ebff',
    icon: '💼',
    features: ['Working capital', 'Equipment purchase', 'Business expansion', 'Trade finance'],
    badge: 'Flexible Terms',
  },
];

const STEPS = [
  { n: '01', title: 'Apply Online', desc: 'Fill out our simple 5-step form in under 10 minutes.' },
  { n: '02', title: 'Credit Review', desc: 'Our team assesses your application within 2–3 business days.' },
  { n: '03', title: 'Approval', desc: 'Receive your personalised offer with final rate and terms.' },
  { n: '04', title: 'Funds Released', desc: 'Money deposited directly to your Bank of Fiji account.' },
];

const FAQS = [
  { q: 'What documents do I need?', a: 'A valid photo ID, recent bank statement (last 3 months), proof of income (payslip or employment letter), and a utility bill for address verification.' },
  { q: 'How quickly can I get my money?', a: 'Personal and vehicle loans are often approved within 2–3 business days. Home and business loans may take 5–10 working days depending on documentation.' },
  { q: 'Is there a penalty for early repayment?', a: 'No. Bank of Fiji does not charge early repayment fees on any loan product. You can pay off your loan ahead of schedule at any time.' },
  { q: 'Can I apply if I\'m self-employed?', a: 'Yes. Self-employed applicants can apply using business financial statements, tax returns, and a business registration certificate as proof of income.' },
  { q: 'What is the minimum loan amount?', a: 'Personal Loans start from FJD 500. Home Loans from FJD 10,000. Vehicle Loans from FJD 2,000. Business Loans from FJD 5,000.' },
];

// ── Calculator component ──────────────────────────────────────────────────────
function Calculator() {
  const [loanType, setLoanType] = useState('Personal Loan');
  const [amount, setAmount]     = useState(10000);
  const [term, setTerm]         = useState(36);

  const product = LOAN_PRODUCTS.find(p => p.type === loanType);
  const r = (parseFloat(product.rate) / 100) / 12;
  const M = amount && term ? (amount * r * Math.pow(1 + r, term)) / (Math.pow(1 + r, term) - 1) : 0;
  const total    = M * term;
  const interest = total - amount;

  const fmt = (v) => `FJD ${parseFloat(v).toLocaleString('en-FJ', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

  return (
    <div style={{
      background: '#fff',
      borderRadius: 20,
      padding: '36px 40px',
      boxShadow: '0 8px 48px rgba(15,45,85,0.12)',
      border: '1px solid #e8edf5',
    }}>
      <div style={{ marginBottom: 28 }}>
        <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: '0.12em', color: '#8a94a6', textTransform: 'uppercase', marginBottom: 8 }}>
          Repayment Calculator
        </div>
        <div style={{ fontFamily: "'Playfair Display', serif", fontSize: 24, color: '#0f2044', lineHeight: 1.2 }}>
          See what you'll pay each month
        </div>
      </div>

      {/* Loan type selector */}
      <div style={{ marginBottom: 20 }}>
        <label style={labelSt}>Loan type</label>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
          {LOAN_PRODUCTS.map(p => (
            <button key={p.type}
              onClick={() => setLoanType(p.type)}
              style={{
                padding: '8px 12px', borderRadius: 8, fontSize: 12, fontWeight: 600,
                cursor: 'pointer', transition: 'all 0.15s',
                background: loanType === p.type ? p.color : '#f5f7fb',
                color: loanType === p.type ? '#fff' : '#374151',
                border: loanType === p.type ? `2px solid ${p.color}` : '2px solid transparent',
              }}>
              {p.icon} {p.type}
            </button>
          ))}
        </div>
      </div>

      {/* Amount slider */}
      <div style={{ marginBottom: 20 }}>
        <label style={labelSt}>
          Loan amount — <span style={{ color: product.color, fontWeight: 700 }}>{fmt(amount)}</span>
        </label>
        <input type="range" min="500" max={loanType === 'Home Loan' ? 500000 : loanType === 'Business Loan' ? 200000 : loanType === 'Vehicle Loan' ? 80000 : 50000}
          step="500" value={amount} onChange={e => setAmount(parseInt(e.target.value))}
          style={{ width: '100%', accentColor: product.color }} />
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, color: '#9ca3af', marginTop: 4 }}>
          <span>FJD 500</span><span>Max FJD {product.maxAmount}</span>
        </div>
      </div>

      {/* Term slider */}
      <div style={{ marginBottom: 28 }}>
        <label style={labelSt}>
          Loan term — <span style={{ color: product.color, fontWeight: 700 }}>{term >= 12 ? `${term / 12} yr${term / 12 > 1 ? 's' : ''}` : `${term} mo`}</span>
        </label>
        <input type="range" min="6" max={loanType === 'Home Loan' ? 300 : loanType === 'Business Loan' ? 120 : loanType === 'Vehicle Loan' ? 84 : 60}
          step="6" value={term} onChange={e => setTerm(parseInt(e.target.value))}
          style={{ width: '100%', accentColor: product.color }} />
      </div>

      {/* Results */}
      <div style={{
        background: `linear-gradient(135deg, ${product.color}12, ${product.color}06)`,
        border: `1px solid ${product.color}30`,
        borderRadius: 14, padding: '22px 24px',
        display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginBottom: 20,
      }}>
        <div>
          <div style={{ fontSize: 11, color: '#6b7280', fontWeight: 600, letterSpacing: '0.06em', textTransform: 'uppercase', marginBottom: 4 }}>Monthly payment</div>
          <div style={{ fontSize: 28, fontWeight: 800, color: product.color, fontFamily: "'Playfair Display', serif" }}>{fmt(M)}</div>
        </div>
        <div>
          <div style={{ fontSize: 11, color: '#6b7280', fontWeight: 600, letterSpacing: '0.06em', textTransform: 'uppercase', marginBottom: 4 }}>Total repayable</div>
          <div style={{ fontSize: 22, fontWeight: 700, color: '#0f2044' }}>{fmt(total)}</div>
        </div>
        <div>
          <div style={{ fontSize: 11, color: '#6b7280', fontWeight: 600, letterSpacing: '0.06em', textTransform: 'uppercase', marginBottom: 4 }}>Total interest</div>
          <div style={{ fontSize: 16, fontWeight: 600, color: '#dc2626' }}>{fmt(interest)}</div>
        </div>
        <div>
          <div style={{ fontSize: 11, color: '#6b7280', fontWeight: 600, letterSpacing: '0.06em', textTransform: 'uppercase', marginBottom: 4 }}>Annual rate</div>
          <div style={{ fontSize: 16, fontWeight: 600, color: '#0f2044' }}>{product.rate}% p.a.</div>
        </div>
      </div>

      <p style={{ fontSize: 11, color: '#9ca3af', marginBottom: 0, lineHeight: 1.5 }}>
        * Indicative only. Actual rate subject to credit assessment. RBF-regulated lending.
      </p>
    </div>
  );
}

const labelSt = { display: 'block', fontSize: 12, fontWeight: 600, color: '#374151', marginBottom: 8, letterSpacing: '0.04em' };

// ── Main page ─────────────────────────────────────────────────────────────────
export default function LoanAdvertPage() {
  const navigate = useNavigate();
  const [openFaq, setOpenFaq] = useState(null);
  const [visible, setVisible] = useState({});
  const sectionRefs = useRef({});

  useEffect(() => {
    const observer = new IntersectionObserver(
      (entries) => entries.forEach(e => {
        if (e.isIntersecting) setVisible(v => ({ ...v, [e.target.dataset.section]: true }));
      }),
      { threshold: 0.1 }
    );
    Object.values(sectionRefs.current).forEach(el => el && observer.observe(el));
    return () => observer.disconnect();
  }, []);

  const setRef = (key) => (el) => { sectionRefs.current[key] = el; };

  return (
    <div style={{ fontFamily: "'DM Sans', 'Segoe UI', sans-serif", background: '#f5f7fb', color: '#0f2044' }}>

      {/* ── HERO ──────────────────────────────────────────────────────────────── */}
      <section style={{
        background: 'linear-gradient(135deg, #0f2044 0%, #1a3a6e 50%, #0e5a8a 100%)',
        padding: '80px 40px 0',
        position: 'relative',
        overflow: 'hidden',
        minHeight: 560,
      }}>
        {/* Decorative circles */}
        <div style={{ position: 'absolute', top: -80, right: -80, width: 400, height: 400, borderRadius: '50%', background: 'rgba(255,255,255,0.04)', pointerEvents: 'none' }} />
        <div style={{ position: 'absolute', bottom: 40, left: -100, width: 300, height: 300, borderRadius: '50%', background: 'rgba(255,255,255,0.03)', pointerEvents: 'none' }} />
        <div style={{ position: 'absolute', top: '30%', right: '15%', width: 150, height: 150, borderRadius: '50%', background: 'rgba(26,86,219,0.2)', pointerEvents: 'none' }} />

        <div style={{ maxWidth: 1100, margin: '0 auto', display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 60, alignItems: 'flex-start' }}>
          {/* Left */}
          <div style={{ paddingBottom: 80 }}>
            <div style={{
              display: 'inline-flex', alignItems: 'center', gap: 8,
              background: 'rgba(255,255,255,0.1)', borderRadius: 20,
              padding: '6px 14px', marginBottom: 24,
              fontSize: 12, fontWeight: 600, color: 'rgba(255,255,255,0.85)',
              letterSpacing: '0.08em', textTransform: 'uppercase',
            }}>
              🏦 Bank of Fiji · Est. 1974
            </div>

            <h1 style={{
              fontFamily: "'Playfair Display', serif",
              fontSize: 52, fontWeight: 700, color: '#fff',
              lineHeight: 1.1, margin: '0 0 20px',
            }}>
              Loans built<br />
              <span style={{ color: '#60a5fa' }}>for Fiji life.</span>
            </h1>

            <p style={{ fontSize: 17, color: 'rgba(255,255,255,0.75)', lineHeight: 1.7, marginBottom: 36, maxWidth: 420 }}>
              Whether you're buying a home, a vehicle, funding your education,
              or growing your business — we have a loan that fits.
              Competitive rates, local decisions, fast approval.
            </p>

            <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
              <button
                onClick={() => navigate('/loan-application')}
                style={{
                  background: '#1a56db', color: '#fff', border: 'none',
                  borderRadius: 10, padding: '14px 32px', fontSize: 15, fontWeight: 700,
                  cursor: 'pointer', transition: 'all 0.2s',
                  boxShadow: '0 4px 20px rgba(26,86,219,0.4)',
                }}
                onMouseEnter={e => e.target.style.background = '#1447c5'}
                onMouseLeave={e => e.target.style.background = '#1a56db'}
              >
                Apply Now →
              </button>
              <button
                onClick={() => document.getElementById('loan-products')?.scrollIntoView({ behavior: 'smooth' })}
                style={{
                  background: 'rgba(255,255,255,0.12)', color: '#fff',
                  border: '1px solid rgba(255,255,255,0.25)',
                  borderRadius: 10, padding: '14px 28px', fontSize: 15, fontWeight: 600,
                  cursor: 'pointer', backdropFilter: 'blur(8px)',
                }}
              >
                Explore loans ↓
              </button>
            </div>

            {/* Trust stats */}
            <div style={{ display: 'flex', gap: 32, marginTop: 48, paddingTop: 32, borderTop: '1px solid rgba(255,255,255,0.12)' }}>
              {[['250K+', 'Customers'], ['50 yrs', 'Banking in Fiji'], ['4', 'Loan types'], ['2–3 days', 'Avg. approval']].map(([n, l]) => (
                <div key={l}>
                  <div style={{ fontSize: 22, fontWeight: 800, color: '#fff' }}>{n}</div>
                  <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.55)', marginTop: 2 }}>{l}</div>
                </div>
              ))}
            </div>
          </div>

          {/* Right — Calculator */}
          <div style={{ position: 'relative', top: 40 }}>
            <Calculator />
          </div>
        </div>
      </section>

      {/* ── LOAN PRODUCTS ─────────────────────────────────────────────────────── */}
      <section id="loan-products" style={{ padding: '100px 40px' }}>
        <div style={{ maxWidth: 1100, margin: '0 auto' }}>
          <div
            ref={setRef('products')}
            data-section="products"
            style={{
              textAlign: 'center', marginBottom: 60,
              opacity: visible.products ? 1 : 0,
              transform: visible.products ? 'none' : 'translateY(30px)',
              transition: 'opacity 0.6s, transform 0.6s',
            }}
          >
            <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: '0.14em', color: '#1a56db', textTransform: 'uppercase', marginBottom: 12 }}>
              Our loan products
            </div>
            <h2 style={{ fontFamily: "'Playfair Display', serif", fontSize: 40, fontWeight: 700, margin: '0 0 16px', color: '#0f2044' }}>
              Find the right loan for you
            </h2>
            <p style={{ fontSize: 16, color: '#6b7280', maxWidth: 520, margin: '0 auto' }}>
              Competitive rates, flexible terms, and approvals made right here in Fiji
              by people who understand your needs.
            </p>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: 24 }}>
            {LOAN_PRODUCTS.map((p, i) => (
              <div
                key={p.type}
                ref={setRef(`card-${i}`)}
                data-section={`card-${i}`}
                style={{
                  background: '#fff',
                  borderRadius: 18,
                  overflow: 'hidden',
                  boxShadow: '0 2px 20px rgba(15,45,85,0.08)',
                  border: '1px solid #e8edf5',
                  transition: 'all 0.35s cubic-bezier(0.34,1.56,0.64,1)',
                  opacity: visible[`card-${i}`] ? 1 : 0,
                  transform: visible[`card-${i}`] ? 'none' : 'translateY(40px)',
                  transitionDelay: `${i * 0.1}s`,
                  cursor: 'pointer',
                }}
                onMouseEnter={e => { e.currentTarget.style.transform = 'translateY(-6px)'; e.currentTarget.style.boxShadow = `0 12px 40px ${p.color}25`; }}
                onMouseLeave={e => { e.currentTarget.style.transform = 'none'; e.currentTarget.style.boxShadow = '0 2px 20px rgba(15,45,85,0.08)'; }}
                onClick={() => navigate('/loan-application')}
              >
                {/* Card top */}
                <div style={{ background: `linear-gradient(135deg, ${p.color}, ${p.color}cc)`, padding: '28px 24px 20px', position: 'relative' }}>
                  <div style={{
                    position: 'absolute', top: 16, right: 16,
                    background: 'rgba(255,255,255,0.2)', backdropFilter: 'blur(8px)',
                    borderRadius: 20, padding: '3px 10px',
                    fontSize: 10, fontWeight: 700, color: '#fff', letterSpacing: '0.06em',
                  }}>
                    {p.badge}
                  </div>
                  <div style={{ fontSize: 36, marginBottom: 10 }}>{p.icon}</div>
                  <div style={{ fontSize: 19, fontWeight: 800, color: '#fff', fontFamily: "'Playfair Display', serif" }}>{p.type}</div>
                  <div style={{ fontSize: 13, color: 'rgba(255,255,255,0.8)', marginTop: 4 }}>{p.tagline}</div>
                </div>

                {/* Card body */}
                <div style={{ padding: '22px 24px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 18, paddingBottom: 16, borderBottom: '1px solid #f3f4f6' }}>
                    <div>
                      <div style={{ fontSize: 10, fontWeight: 700, color: '#9ca3af', textTransform: 'uppercase', letterSpacing: '0.08em' }}>From</div>
                      <div style={{ fontSize: 26, fontWeight: 800, color: p.color }}>{p.rate}<span style={{ fontSize: 14, fontWeight: 500, color: '#6b7280' }}>% p.a.</span></div>
                    </div>
                    <div style={{ textAlign: 'right' }}>
                      <div style={{ fontSize: 10, fontWeight: 700, color: '#9ca3af', textTransform: 'uppercase', letterSpacing: '0.08em' }}>Up to</div>
                      <div style={{ fontSize: 18, fontWeight: 800, color: '#0f2044' }}>FJD {p.maxAmount}</div>
                      <div style={{ fontSize: 11, color: '#9ca3af' }}>over {p.maxTerm}</div>
                    </div>
                  </div>

                  <ul style={{ margin: 0, padding: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 7 }}>
                    {p.features.map(f => (
                      <li key={f} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: '#374151' }}>
                        <span style={{ width: 18, height: 18, borderRadius: '50%', background: p.accent, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 9, color: p.color, fontWeight: 700, flexShrink: 0 }}>✓</span>
                        {f}
                      </li>
                    ))}
                  </ul>

                  <button
                    style={{
                      marginTop: 20, width: '100%', padding: '11px 0',
                      background: p.accent, color: p.color,
                      border: `1.5px solid ${p.color}40`,
                      borderRadius: 10, fontSize: 13, fontWeight: 700,
                      cursor: 'pointer', transition: 'all 0.15s',
                    }}
                    onMouseEnter={e => { e.target.style.background = p.color; e.target.style.color = '#fff'; }}
                    onMouseLeave={e => { e.target.style.background = p.accent; e.target.style.color = p.color; }}
                  >
                    Apply for {p.type} →
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── HOW IT WORKS ──────────────────────────────────────────────────────── */}
      <section style={{ background: '#0f2044', padding: '90px 40px' }}>
        <div style={{ maxWidth: 1100, margin: '0 auto' }}>
          <div style={{ textAlign: 'center', marginBottom: 60 }}>
            <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: '0.14em', color: '#60a5fa', textTransform: 'uppercase', marginBottom: 12 }}>Simple process</div>
            <h2 style={{ fontFamily: "'Playfair Display', serif", fontSize: 38, fontWeight: 700, color: '#fff', margin: 0 }}>
              From application to funds in 4 steps
            </h2>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))', gap: 24 }}>
            {STEPS.map((s, i) => (
              <div key={s.n} style={{
                background: 'rgba(255,255,255,0.06)',
                border: '1px solid rgba(255,255,255,0.1)',
                borderRadius: 16, padding: '28px 24px',
                position: 'relative', overflow: 'hidden',
              }}>
                <div style={{
                  position: 'absolute', top: -10, right: -10,
                  fontSize: 80, fontWeight: 900, color: 'rgba(255,255,255,0.04)',
                  lineHeight: 1, userSelect: 'none',
                }}>
                  {s.n}
                </div>
                <div style={{
                  width: 44, height: 44, borderRadius: 12,
                  background: 'linear-gradient(135deg, #1a56db, #60a5fa)',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  fontSize: 18, fontWeight: 800, color: '#fff', marginBottom: 16,
                }}>
                  {s.n}
                </div>
                <div style={{ fontFamily: "'Playfair Display', serif", fontSize: 18, color: '#fff', marginBottom: 8 }}>{s.title}</div>
                <div style={{ fontSize: 13, color: 'rgba(255,255,255,0.6)', lineHeight: 1.6 }}>{s.desc}</div>
              </div>
            ))}
          </div>

          <div style={{ textAlign: 'center', marginTop: 52 }}>
            <button
              onClick={() => navigate('/loan-application')}
              style={{
                background: '#1a56db', color: '#fff', border: 'none',
                borderRadius: 10, padding: '15px 40px', fontSize: 16, fontWeight: 700,
                cursor: 'pointer', boxShadow: '0 4px 24px rgba(26,86,219,0.45)',
              }}
            >
              Start Your Application →
            </button>
          </div>
        </div>
      </section>

      {/* ── WHY BANK OF FIJI ──────────────────────────────────────────────────── */}
      <section style={{ padding: '90px 40px', background: '#fff' }}>
        <div style={{ maxWidth: 1100, margin: '0 auto' }}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 80, alignItems: 'center' }}>
            <div>
              <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: '0.14em', color: '#1a56db', textTransform: 'uppercase', marginBottom: 14 }}>Why choose us</div>
              <h2 style={{ fontFamily: "'Playfair Display', serif", fontSize: 38, fontWeight: 700, margin: '0 0 20px', lineHeight: 1.2, color: '#0f2044' }}>
                Banking that<br />understands Fiji.
              </h2>
              <p style={{ fontSize: 15, color: '#6b7280', lineHeight: 1.8, marginBottom: 32 }}>
                Since 1974, Bank of Fiji has been helping families and businesses grow.
                Our loans are assessed locally, approved locally, and designed around real Fijian life — not overseas algorithms.
              </p>

              {[
                ['⚡', 'Fast decisions', 'Credit assessed by local experts who understand the Fijian economy.'],
                ['🔒', 'RBF regulated', 'Fully licensed and regulated by the Reserve Bank of Fiji.'],
                ['💰', 'No early repayment fees', 'Pay off your loan whenever you\'re ready — no penalties, ever.'],
                ['📱', 'Apply online anytime', 'Submit your application from home in under 10 minutes.'],
              ].map(([icon, title, desc]) => (
                <div key={title} style={{ display: 'flex', gap: 16, marginBottom: 22 }}>
                  <div style={{
                    width: 44, height: 44, borderRadius: 12,
                    background: '#e8f0fe', display: 'flex', alignItems: 'center',
                    justifyContent: 'center', fontSize: 20, flexShrink: 0,
                  }}>{icon}</div>
                  <div>
                    <div style={{ fontWeight: 700, fontSize: 14, color: '#0f2044', marginBottom: 3 }}>{title}</div>
                    <div style={{ fontSize: 13, color: '#6b7280', lineHeight: 1.5 }}>{desc}</div>
                  </div>
                </div>
              ))}
            </div>

            {/* Rates summary card */}
            <div style={{
              background: 'linear-gradient(160deg, #0f2044, #1a3a6e)',
              borderRadius: 20, padding: '40px 36px',
              boxShadow: '0 20px 60px rgba(15,45,85,0.2)',
            }}>
              <div style={{ fontSize: 12, fontWeight: 700, color: 'rgba(255,255,255,0.5)', letterSpacing: '0.1em', textTransform: 'uppercase', marginBottom: 28 }}>
                Current rates at a glance
              </div>
              {LOAN_PRODUCTS.map((p, i) => (
                <div key={p.type} style={{
                  display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                  padding: '14px 0',
                  borderBottom: i < LOAN_PRODUCTS.length - 1 ? '1px solid rgba(255,255,255,0.08)' : 'none',
                }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                    <span style={{ fontSize: 20 }}>{p.icon}</span>
                    <div>
                      <div style={{ fontSize: 14, fontWeight: 600, color: '#fff' }}>{p.type}</div>
                      <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.45)' }}>Up to FJD {p.maxAmount}</div>
                    </div>
                  </div>
                  <div style={{ textAlign: 'right' }}>
                    <div style={{ fontSize: 22, fontWeight: 800, color: '#60a5fa' }}>{p.rate}%</div>
                    <div style={{ fontSize: 10, color: 'rgba(255,255,255,0.4)' }}>p.a.</div>
                  </div>
                </div>
              ))}
              <div style={{ marginTop: 28, paddingTop: 20, borderTop: '1px solid rgba(255,255,255,0.1)', fontSize: 11, color: 'rgba(255,255,255,0.35)', lineHeight: 1.6 }}>
                * Rates from. Subject to credit assessment and RBF guidelines.
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ── FAQ ───────────────────────────────────────────────────────────────── */}
      <section style={{ padding: '90px 40px', background: '#f5f7fb' }}>
        <div style={{ maxWidth: 760, margin: '0 auto' }}>
          <div style={{ textAlign: 'center', marginBottom: 52 }}>
            <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: '0.14em', color: '#1a56db', textTransform: 'uppercase', marginBottom: 12 }}>FAQs</div>
            <h2 style={{ fontFamily: "'Playfair Display', serif", fontSize: 36, fontWeight: 700, margin: 0, color: '#0f2044' }}>
              Common questions
            </h2>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {FAQS.map((faq, i) => (
              <div key={i} style={{
                background: '#fff', borderRadius: 14,
                border: `1.5px solid ${openFaq === i ? '#1a56db40' : '#e8edf5'}`,
                overflow: 'hidden',
                boxShadow: openFaq === i ? '0 4px 20px rgba(26,86,219,0.08)' : 'none',
                transition: 'all 0.2s',
              }}>
                <button
                  onClick={() => setOpenFaq(openFaq === i ? null : i)}
                  style={{
                    width: '100%', padding: '20px 24px',
                    display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                    background: 'none', border: 'none', cursor: 'pointer',
                    textAlign: 'left',
                  }}
                >
                  <span style={{ fontSize: 15, fontWeight: 600, color: '#0f2044' }}>{faq.q}</span>
                  <span style={{
                    fontSize: 18, color: '#1a56db', fontWeight: 700,
                    transform: openFaq === i ? 'rotate(45deg)' : 'none',
                    transition: 'transform 0.2s', flexShrink: 0, marginLeft: 16,
                  }}>+</span>
                </button>
                {openFaq === i && (
                  <div style={{ padding: '0 24px 20px', fontSize: 14, color: '#6b7280', lineHeight: 1.7 }}>
                    {faq.a}
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── CTA BANNER ────────────────────────────────────────────────────────── */}
      <section style={{
        background: 'linear-gradient(135deg, #1a56db 0%, #0e5a8a 100%)',
        padding: '80px 40px', textAlign: 'center',
      }}>
        <div style={{ maxWidth: 600, margin: '0 auto' }}>
          <div style={{ fontSize: 42, marginBottom: 16 }}>🌴</div>
          <h2 style={{ fontFamily: "'Playfair Display', serif", fontSize: 38, fontWeight: 700, color: '#fff', margin: '0 0 16px' }}>
            Ready to get started?
          </h2>
          <p style={{ fontSize: 16, color: 'rgba(255,255,255,0.8)', marginBottom: 36, lineHeight: 1.7 }}>
            Apply online in minutes. No branch visit required.
            Our team reviews every application personally.
          </p>
          <div style={{ display: 'flex', gap: 14, justifyContent: 'center', flexWrap: 'wrap' }}>
            <button
              onClick={() => navigate('/loan-application')}
              style={{
                background: '#fff', color: '#1a56db', border: 'none',
                borderRadius: 10, padding: '15px 36px', fontSize: 16, fontWeight: 800,
                cursor: 'pointer', boxShadow: '0 4px 20px rgba(0,0,0,0.15)',
              }}
            >
              Apply Now →
            </button>
            <button
              onClick={() => navigate('/loans')}
              style={{
                background: 'rgba(255,255,255,0.15)', color: '#fff',
                border: '1.5px solid rgba(255,255,255,0.35)',
                borderRadius: 10, padding: '15px 28px', fontSize: 16, fontWeight: 600,
                cursor: 'pointer',
              }}
            >
              View my loans
            </button>
          </div>
          <p style={{ marginTop: 28, fontSize: 12, color: 'rgba(255,255,255,0.5)' }}>
            Bank of Fiji is regulated by the Reserve Bank of Fiji. Lending criteria apply. Terms &amp; conditions apply.
          </p>
        </div>
      </section>
    </div>
  );
}