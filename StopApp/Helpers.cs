using System.Diagnostics;
using System.Drawing.Drawing2D;

namespace StopApp
{
    /// <summary>Thèmes de couleurs (les mêmes que sur le téléphone).</summary>
    public class Theme
    {
        public string Id, Name;
        public Color A, B, C, Accent;
        public Theme(string id, string name, string a, string b, string c, string accent)
        {
            Id = id; Name = name;
            A = ColorTranslator.FromHtml(a); B = ColorTranslator.FromHtml(b);
            C = ColorTranslator.FromHtml(c); Accent = ColorTranslator.FromHtml(accent);
        }

        public static readonly List<Theme> All = new()
        {
            new Theme("corail", "Corail", "#FF416C", "#FF4B2B", "#6C5CE7", "#FF416C"),
            new Theme("ocean", "Océan", "#0052D4", "#4364F7", "#4FACFE", "#0052D4"),
            new Theme("foret", "Forêt", "#0B6E4F", "#11998E", "#38D98E", "#0B8457"),
            new Theme("aurore", "Aurore", "#4A00E0", "#8E2DE2", "#FF6CAB", "#7B2FF7"),
            new Theme("soleil", "Soleil", "#FF5E62", "#FF8A3D", "#F7B733", "#F0643C"),
            new Theme("nuit", "Nuit", "#0F2027", "#203A43", "#2C5364", "#2C7DA0"),
        };

        public static Theme Current => All.FirstOrDefault(t => t.Id == Store.D.Theme) ?? All[0];

        /// <summary>Peint le design choisi aux couleurs du thème.</summary>
        public void Paint(Graphics g, Rectangle r) => Art.Draw(g, r, this, Look.Current);

        /// <summary>Peint le simple dégradé du thème.</summary>
        public void PaintGradient(Graphics g, Rectangle r)
        {
            if (r.Width <= 0 || r.Height <= 0) return;
            using var br = new LinearGradientBrush(r, A, C, 35f);
            br.InterpolationColors = new ColorBlend
            {
                Colors = new[] { A, B, C },
                Positions = new[] { 0f, 0.5f, 1f }
            };
            g.FillRectangle(br, r);
        }
    }

    /// <summary>Panneau qui affiche le dégradé du thème.</summary>
    public class GradientPanel : Panel
    {
        public GradientPanel()
        {
            DoubleBuffered = true;
            SetStyle(ControlStyles.ResizeRedraw | ControlStyles.AllPaintingInWmPaint | ControlStyles.UserPaint, true);
        }
        protected override void OnPaintBackground(PaintEventArgs e) => Theme.Current.Paint(e.Graphics, ClientRectangle);
    }

    public static class Texts
    {
        static readonly Random R = new();
        public static string Pick(string[] l) => l[R.Next(l.Length)];

        public static readonly string[] Home =
        {
            "Chaque minute reprise t'appartient.",
            "Le scroll peut attendre. Ta vie, non.",
            "Petit à petit, tu reprends le contrôle.",
            "Ce que tu fais de ton temps, c'est ce que tu deviens.",
            "La discipline, c'est choisir ce que tu veux le plus plutôt que ce que tu veux maintenant.",
            "Tu n'as pas besoin de motivation tous les jours, juste de régularité.",
            "Aujourd'hui compte. Fais-en un bon jour.",
        };

        public static readonly string[] Blocked =
        {
            "Pas maintenant. Tu vaux mieux que ça.",
            "Respire un coup. Qu'est-ce que tu voulais vraiment faire ?",
            "Bien joué de résister ! Chaque refus te rend plus fort.",
            "Ton futur toi te remercie.",
            "Tu as choisi ce blocage pour une bonne raison. Fais-lui confiance.",
            "L'envie va passer. Ta fierté, elle, va rester.",
            "Encore une victoire pour toi aujourd'hui !",
        };

        public static readonly string[] Ideas =
        {
            "💧 Bois un grand verre d'eau",
            "💪 Fais 10 pompes ou 20 squats",
            "🚶 Va marcher 5 minutes dehors",
            "📖 Lis 5 pages d'un livre",
            "🧘 Ferme les yeux et respire lentement 10 fois",
            "🧹 Range ton bureau",
            "📝 Écris 3 choses que tu veux faire aujourd'hui",
            "📚 Avance 10 minutes sur tes devoirs ou ton travail",
            "🤸 Étire-toi 2 minutes, ton dos te remerciera",
            "🎸 Pratique un truc que tu veux apprendre",
        };

        public static readonly string[] Quit =
        {
            "Tu as installé stop#app pour une bonne raison. Elle est toujours vraie ?",
            "Les moments où on veut abandonner sont souvent juste avant les vrais progrès.",
            "Et si tu attendais demain pour décider ?",
            "Tu as déjà fait le plus dur : commencer. Ne lâche pas maintenant.",
        };
    }

    /// <summary>Lance une commande Windows sans fenêtre.</summary>
    public static class Shell
    {
        public static int Run(string file, string args)
        {
            try
            {
                var p = Process.Start(new ProcessStartInfo(file, args)
                {
                    CreateNoWindow = true,
                    UseShellExecute = false,
                    WindowStyle = ProcessWindowStyle.Hidden,
                    RedirectStandardOutput = true,
                    RedirectStandardError = true,
                });
                if (p == null) return -1;
                p.StandardOutput.ReadToEnd();
                p.WaitForExit(15000);
                return p.ExitCode;
            }
            catch { return -1; }
        }
    }

    /// <summary>Démarrage automatique : au démarrage de la session, et relance chaque minute si on le ferme de force.</summary>
    public static class AutoStart
    {
        const string LogonTask = "stopapp";
        const string GuardTask = "stopapp-garde";

        public static void Register(string exe)
        {
            var tr = $"\\\"{exe}\\\" --background";
            Shell.Run("schtasks", $"/Create /F /TN \"{LogonTask}\" /SC ONLOGON /RL HIGHEST /TR \"{tr}\"");
            Shell.Run("schtasks", $"/Create /F /TN \"{GuardTask}\" /SC MINUTE /MO 1 /RL HIGHEST /TR \"{tr}\"");
        }

        /// <summary>Seulement le démarrage de session (après « Fermer stop#app »).</summary>
        public static void RegisterLogonOnly(string exe)
        {
            var tr = $"\\\"{exe}\\\" --background";
            Shell.Run("schtasks", $"/Create /F /TN \"{LogonTask}\" /SC ONLOGON /RL HIGHEST /TR \"{tr}\"");
        }

        public static void Unregister()
        {
            Shell.Run("schtasks", $"/Delete /F /TN \"{LogonTask}\"");
            Shell.Run("schtasks", $"/Delete /F /TN \"{GuardTask}\"");
        }
    }

    /// <summary>Blocage des sites dans TOUS les navigateurs, via le fichier hosts de Windows.</summary>
    public static class Hosts
    {
        static string FilePath => Path.Combine(Environment.SystemDirectory, "drivers", "etc", "hosts");
        const string Begin = "# >>> stop#app (ne pas modifier)";
        const string End = "# <<< stop#app";

        /// <summary>Écrit la liste des sites bloqués (liste vide = on retire tout). Renvoie true si le fichier a changé.</summary>
        public static bool Apply(IEnumerable<string> sites)
        {
            try
            {
                var text = File.Exists(FilePath) ? File.ReadAllText(FilePath) : "";
                var clean = Strip(text).TrimEnd('\r', '\n') + "\r\n";
                var list = sites.Where(s => !string.IsNullOrWhiteSpace(s)).Distinct().ToList();
                var wanted = clean;
                if (list.Count > 0)
                {
                    var lines = new List<string> { Begin };
                    foreach (var s in list)
                        foreach (var prefix in new[] { "", "www.", "m.", "mobile." })
                            lines.Add($"0.0.0.0 {prefix}{s}");
                    lines.Add(End);
                    wanted = clean + "\r\n" + string.Join("\r\n", lines) + "\r\n";
                }
                if (wanted == text) return false;
                var attr = File.Exists(FilePath) ? File.GetAttributes(FilePath) : FileAttributes.Normal;
                if (attr.HasFlag(FileAttributes.ReadOnly)) File.SetAttributes(FilePath, attr & ~FileAttributes.ReadOnly);
                File.WriteAllText(FilePath, wanted);
                Shell.Run("ipconfig", "/flushdns");
                return true;
            }
            catch { return false; }
        }

        static string Strip(string text)
        {
            int b = text.IndexOf(Begin, StringComparison.Ordinal);
            if (b < 0) return text;
            int e = text.IndexOf(End, b, StringComparison.Ordinal);
            if (e < 0) return text.Substring(0, b);
            return text.Substring(0, b) + text.Substring(e + End.Length);
        }

        /// <summary>"https://www.YouTube.com/watch" -> "youtube.com"</summary>
        public static string Normalize(string raw)
        {
            var s = (raw ?? "").Trim().ToLowerInvariant();
            foreach (var p in new[] { "https://", "http://" }) if (s.StartsWith(p)) s = s.Substring(p.Length);
            if (s.StartsWith("www.")) s = s.Substring(4);
            int i = s.IndexOfAny(new[] { '/', '?', '#', ':' });
            if (i >= 0) s = s.Substring(0, i);
            return s.Contains('.') ? s : "";
        }
    }
}
