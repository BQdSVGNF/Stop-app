using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.Runtime.CompilerServices;

namespace StopApp
{
    /// <summary>Les designs de fond (les mêmes que sur le téléphone).</summary>
    public enum ArtStyle { MINIMAL, GRADIENT, SOFT3D, CONTOUR, HALFTONE, BLUEPRINT }

    public static class Look
    {
        public static readonly (ArtStyle style, string label)[] Styles =
        {
            (ArtStyle.MINIMAL, "Minimal"),
            (ArtStyle.GRADIENT, "Dégradé"),
            (ArtStyle.SOFT3D, "Soft 3D"),
            (ArtStyle.CONTOUR, "Lignes de niveau"),
            (ArtStyle.HALFTONE, "Trame"),
            (ArtStyle.BLUEPRINT, "Blueprint"),
        };

        public static ArtStyle Current
        {
            get
            {
                // Nouveau style par défaut : Minimal (une seule fois, ensuite le choix compte)
                if (!Store.D.MinimalV1) { Store.D.MinimalV1 = true; Store.D.Style = "MINIMAL"; Store.Save(); }
                return Enum.TryParse<ArtStyle>(Store.D.Style, out var s) ? s : ArtStyle.MINIMAL;
            }
        }

        /// <summary>Design clair : les textes passent en foncé.</summary>
        public static bool Light => Current == ArtStyle.SOFT3D || Current == ArtStyle.MINIMAL;

        public static readonly Color Ink = ColorTranslator.FromHtml("#1E1B2E");

        static readonly ConditionalWeakTable<Control, object> originals = new();

        /// <summary>Textes blancs → foncés sur un design clair (et l'inverse). Les boutons gardent leurs couleurs.</summary>
        public static void ApplyFore(Control root)
        {
            foreach (Control c in root.Controls)
            {
                if (c is Button) continue;
                if (c is Label l)
                {
                    var orig = (Color)originals.GetValue(l, k => k.ForeColor);
                    bool white = orig.R > 225 && orig.G > 225 && orig.B > 225;
                    if (white) l.ForeColor = Light ? Color.FromArgb(orig.A, Ink) : orig;
                }
                ApplyFore(c);
            }
        }
    }

    /// <summary>Dessine le design choisi (mis en cache par taille).</summary>
    public static class Art
    {
        static readonly Dictionary<string, Bitmap> cache = new();

        public static void Draw(Graphics g, Rectangle r, Theme t, ArtStyle style)
        {
            if (r.Width <= 0 || r.Height <= 0) return;
            if (style == ArtStyle.GRADIENT) { t.PaintGradient(g, r); return; }
            if (style == ArtStyle.MINIMAL) { using var b = new SolidBrush(ColorTranslator.FromHtml("#F2F2F7")); g.FillRectangle(b, r); return; }
            var key = $"{style}|{t.Id}|{r.Width}x{r.Height}";
            if (!cache.TryGetValue(key, out var bmp))
            {
                if (cache.Count > 6) { foreach (var b in cache.Values) b.Dispose(); cache.Clear(); }
                bmp = Render(r.Width, r.Height, t, style);
                cache[key] = bmp;
            }
            g.DrawImageUnscaled(bmp, r.Location);
        }

        /// <summary>Petit aperçu (pas mis en cache partagé).</summary>
        public static Bitmap Preview(int w, int h, Theme t, ArtStyle style)
        {
            if (style == ArtStyle.GRADIENT || style == ArtStyle.MINIMAL)
            {
                var b = new Bitmap(w, h);
                using var g = Graphics.FromImage(b);
                Draw(g, new Rectangle(0, 0, w, h), t, style);
                if (style == ArtStyle.MINIMAL)
                {
                    // Aperçu : une petite carte blanche
                    using var card = new SolidBrush(Color.White);
                    g.FillRectangle(card, 12, 12, w - 24, h / 2 - 6);
                }
                return b;
            }
            return Render(w, h, t, style);
        }

        static Bitmap Render(int w, int h, Theme t, ArtStyle style)
        {
            var bmp = new Bitmap(w, h, PixelFormat.Format32bppPArgb);
            using var g = Graphics.FromImage(bmp);
            g.SmoothingMode = SmoothingMode.AntiAlias;
            switch (style)
            {
                case ArtStyle.SOFT3D: Soft3D(g, w, h, t); break;
                case ArtStyle.CONTOUR: Contour(g, w, h, t); break;
                case ArtStyle.HALFTONE: Halftone(g, w, h, t); break;
                case ArtStyle.BLUEPRINT: Blueprint(g, w, h, t); break;
            }
            return bmp;
        }

        static Color Mix(Color a, Color b, float k) => Color.FromArgb(
            (int)(a.R + (b.R - a.R) * k), (int)(a.G + (b.G - a.G) * k), (int)(a.B + (b.B - a.B) * k));
        static Color Darken(Color a, float f) => Color.FromArgb((int)(a.R * f), (int)(a.G * f), (int)(a.B * f));

        // ---------- Soft 3D : formes douces avec ombres et reflets (couleurs du modèle) ----------

        record Tone(Color Base, Color Light, Color Dark, Color Shadow);
        static Color H(string hex) => ColorTranslator.FromHtml(hex);
        static readonly Tone Violet = new(H("#9A7BF5"), H("#F4F0FF"), H("#7350E6"), H("#8C7BE0"));
        static readonly Tone Peach = new(H("#FF9A6E"), H("#FFF3EC"), H("#FF7A45"), H("#F0A080"));
        static readonly Tone Mint = new(H("#5BD3B2"), H("#F0FFFA"), H("#2DB38F"), H("#7FD0C0"));
        static readonly Tone Sun = new(H("#FAD36A"), H("#FFFBEA"), H("#F2B935"), H("#E9C070"));

        static void Soft3D(Graphics g, int w, int h, Theme t)
        {
            var rect = new Rectangle(0, 0, w, h);
            using (var bg = new LinearGradientBrush(rect, Color.White, Color.White, 55f))
            {
                bg.InterpolationColors = new ColorBlend
                {
                    Colors = new[] { H("#F3E6FF"), H("#E6ECFF"), H("#FDEFE6") },
                    Positions = new[] { 0f, 0.5f, 1f },
                };
                g.FillRectangle(bg, rect);
            }
            float s = Math.Min(w, h);
            if (w < 200)
            {
                // Petit aperçu : la composition du modèle
                Sphere(g, w * 0.25f, h * 0.36f, s * 0.20f, Violet);
                Blob(g, w * 0.52f, h * 0.47f, s * 0.17f, s * 0.135f, 12f, Peach);
                Blob(g, w * 0.74f, h * 0.27f, s * 0.15f, s * 0.135f, -20f, Mint);
                Sphere(g, w * 0.77f, h * 0.62f, s * 0.08f, Sun);
            }
            else if (h < w * 0.45f)
            {
                // Bandeau (en-tête) : le texte est à gauche, les formes à droite
                Sphere(g, w * 0.74f, h * 0.38f, s * 0.30f, Violet);
                Blob(g, w * 0.93f, h * 0.30f, s * 0.20f, s * 0.17f, -20f, Mint);
                Blob(g, w * 0.86f, h * 0.98f, s * 0.19f, s * 0.15f, 12f, Peach);
                Sphere(g, w * 0.60f, h * 0.86f, s * 0.09f, Sun);
            }
            else
            {
                // Fenêtre entière : formes à moitié sorties sur les bords, le texte reste lisible
                Sphere(g, w * 1.00f, h * 0.10f, s * 0.26f, Violet);
                Blob(g, w * 0.00f, h * 0.45f, s * 0.17f, s * 0.15f, -20f, Mint);
                Blob(g, w * 1.00f, h * 0.72f, s * 0.19f, s * 0.15f, 12f, Peach);
                Sphere(g, w * 0.08f, h * 0.94f, s * 0.11f, Sun);
            }
        }

        /// <summary>Ombre floue : dégradé de la couleur d'ombre vers transparent.</summary>
        static void SoftShadow(Graphics g, GraphicsPath shape, Color c)
        {
            using var pb = new PathGradientBrush(shape)
            {
                CenterColor = Color.FromArgb(115, c),
                SurroundColors = new[] { Color.FromArgb(0, c) },
            };
            pb.FocusScales = new PointF(0.45f, 0.45f);
            g.FillPath(pb, shape);
        }

        /// <summary>Reflet : lumière en haut à gauche, couleur pleine, bord plus foncé.</summary>
        static void Shine(Graphics g, GraphicsPath shape, RectangleF box, Tone t)
        {
            using var pb = new PathGradientBrush(shape)
            {
                CenterPoint = new PointF(box.X + box.Width * 0.31f, box.Y + box.Height * 0.29f),
            };
            pb.InterpolationColors = new ColorBlend
            {
                Colors = new[] { t.Dark, t.Base, t.Light },
                Positions = new[] { 0f, 0.55f, 1f },
            };
            g.FillPath(pb, shape);
        }

        static void Sphere(Graphics g, float cx, float cy, float r, Tone t)
        {
            using (var sh = new GraphicsPath())
            {
                sh.AddEllipse(cx - r * 1.2f, cy + r * 0.35f - r * 1.2f, r * 2.4f, r * 2.4f);
                SoftShadow(g, sh, t.Shadow);
            }
            var box = new RectangleF(cx - r, cy - r, r * 2, r * 2);
            using var p = new GraphicsPath();
            p.AddEllipse(box);
            Shine(g, p, box, t);
        }

        static GraphicsPath RoundRect(RectangleF r, float rad)
        {
            var p = new GraphicsPath();
            float d = Math.Min(rad * 2, Math.Min(r.Width, r.Height));
            p.AddArc(r.X, r.Y, d, d, 180, 90);
            p.AddArc(r.Right - d, r.Y, d, d, 270, 90);
            p.AddArc(r.Right - d, r.Bottom - d, d, d, 0, 90);
            p.AddArc(r.X, r.Bottom - d, d, d, 90, 90);
            p.CloseFigure();
            return p;
        }

        static void Blob(Graphics g, float cx, float cy, float rw, float rh, float angle, Tone t)
        {
            var state = g.Save();
            g.TranslateTransform(cx, cy);
            g.RotateTransform(angle);
            using (var sh = RoundRect(new RectangleF(-rw * 1.3f, -rh * 1.3f + rh * 0.35f, rw * 2.6f, rh * 2.6f), rh * 1.1f))
                SoftShadow(g, sh, t.Shadow);
            var box = new RectangleF(-rw, -rh, rw * 2, rh * 2);
            using (var p = RoundRect(box, rh * 0.72f))
                Shine(g, p, box, t);
            g.Restore(state);
        }

        // ---------- Lignes de niveau ----------

        static void Contour(Graphics g, int w, int h, Theme t)
        {
            // Couleurs du modèle : vert forêt profond et lignes vert citron
            g.Clear(H("#0F2A21"));
            var line = H("#B9E772");
            float step = 8f;
            int gw = (int)(w / step) + 2, gh = (int)(h / step) + 2;
            var f = new float[gh, gw];
            float[][] bumps =
            {
                new[] { 0.15f, 0.12f, 1.0f, 0.28f }, new[] { 0.80f, 0.20f, -0.9f, 0.30f },
                new[] { 0.45f, 0.48f, 0.8f, 0.25f }, new[] { 0.10f, 0.75f, -0.7f, 0.30f },
                new[] { 0.85f, 0.70f, 1.1f, 0.28f }, new[] { 0.50f, 0.95f, -0.8f, 0.30f },
                new[] { 0.30f, 0.30f, -0.5f, 0.18f },
            };
            float diag = (float)Math.Sqrt((double)w * w + (double)h * h);
            float lo = float.MaxValue, hi = float.MinValue;
            for (int j = 0; j < gh; j++)
                for (int i = 0; i < gw; i++)
                {
                    float x = i * step, y = j * step, v = 0;
                    foreach (var b in bumps)
                    {
                        float dx = (x - b[0] * w) / (b[3] * diag), dy = (y - b[1] * h) / (b[3] * diag);
                        v += b[2] * (float)Math.Exp(-(dx * dx + dy * dy));
                    }
                    v += 0.08f * (float)Math.Sin(x / diag * 9f + y / diag * 5f);
                    f[j, i] = v;
                    lo = Math.Min(lo, v); hi = Math.Max(hi, v);
                }
            const int levels = 40;
            using var thin = new Pen(Color.FromArgb(95, line), 1f);
            using var bold = new Pen(Color.FromArgb(220, line), 1.6f);
            var pts = new List<PointF>(4);
            for (int k = 1; k < levels; k++)
            {
                float iso = lo + (hi - lo) * k / levels;
                var pen = k % 6 == 0 ? bold : thin;
                for (int j = 0; j < gh - 1; j++)
                    for (int i = 0; i < gw - 1; i++)
                    {
                        float a = f[j, i], b = f[j, i + 1], c = f[j + 1, i + 1], d = f[j + 1, i];
                        float x0 = i * step, y0 = j * step, x1 = x0 + step, y1 = y0 + step;
                        pts.Clear();
                        Edge(pts, iso, a, b, x0, y0, x1, y0);
                        Edge(pts, iso, b, c, x1, y0, x1, y1);
                        Edge(pts, iso, c, d, x1, y1, x0, y1);
                        Edge(pts, iso, d, a, x0, y1, x0, y0);
                        for (int p = 0; p + 1 < pts.Count; p += 2) g.DrawLine(pen, pts[p], pts[p + 1]);
                    }
            }
        }

        static void Edge(List<PointF> pts, float iso, float v1, float v2, float xa, float ya, float xb, float yb)
        {
            if ((v1 < iso) == (v2 < iso)) return;
            float k = (iso - v1) / (v2 - v1);
            pts.Add(new PointF(xa + (xb - xa) * k, ya + (yb - ya) * k));
        }

        // ---------- Trame ----------

        static void Halftone(Graphics g, int w, int h, Theme t)
        {
            t.PaintGradient(g, new Rectangle(0, 0, w, h));
            using var br = new SolidBrush(Color.FromArgb(60, 255, 255, 255));
            float step = 16f;
            for (float y = step / 2; y < h; y += step)
                for (float x = step / 2; x < w; x += step)
                {
                    float k = (x / w + y / h) / 2f, r = step * (0.08f + 0.30f * k);
                    g.FillEllipse(br, x - r, y - r, r * 2, r * 2);
                }
        }

        // ---------- Blueprint ----------

        static void Blueprint(Graphics g, int w, int h, Theme t)
        {
            g.Clear(Mix(t.A, ColorTranslator.FromHtml("#0D1B2A"), 0.75f));
            using var thin = new Pen(Color.FromArgb(28, 255, 255, 255), 1f);
            using var bold = new Pen(Color.FromArgb(55, 255, 255, 255), 1.5f);
            float step = 24f;
            int i = 0;
            for (float x = 0; x < w; x += step, i++) g.DrawLine(i % 4 == 0 ? bold : thin, x, 0, x, h);
            i = 0;
            for (float y = 0; y < h; y += step, i++) g.DrawLine(i % 4 == 0 ? bold : thin, 0, y, w, y);
            using var circle = new Pen(Color.FromArgb(70, 255, 255, 255), 1.5f);
            float s = Math.Min(w, h);
            g.DrawEllipse(circle, w * 0.85f - s * 0.22f, h * 0.18f - s * 0.22f, s * 0.44f, s * 0.44f);
            g.DrawEllipse(circle, w * 0.85f - s * 0.12f, h * 0.18f - s * 0.12f, s * 0.24f, s * 0.24f);
        }
    }
}
