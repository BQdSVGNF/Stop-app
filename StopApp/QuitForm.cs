namespace StopApp
{
    /// <summary>
    /// Questionnaire avant de fermer / désinstaller stop#app (comme sur le téléphone) :
    /// que veux-tu faire → pourquoi → tes progrès → 30 secondes de réflexion.
    /// </summary>
    public class QuitForm : Form
    {
        readonly Label title = new(), sub = new();
        readonly FlowLayoutPanel options = new();
        readonly System.Windows.Forms.Timer timer = new() { Interval = 1000 };
        int secondsLeft;

        /// <summary>Choix final : "quit" (fermer jusqu'au prochain démarrage) ou "uninstall".</summary>
        public string Result { get; private set; } = "";

        public QuitForm()
        {
            Text = "stop#app";
            Icon = AppIcon.Get();
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = MinimizeBox = false;
            StartPosition = FormStartPosition.CenterScreen;
            ClientSize = new Size(520, 560);
            TopMost = true;
            DoubleBuffered = true;

            var col = new TableLayoutPanel { Dock = DockStyle.Fill, BackColor = Color.Transparent, ColumnCount = 1, RowCount = 4, Padding = new Padding(30) };
            col.RowStyles.Add(new RowStyle(SizeType.AutoSize));
            col.RowStyles.Add(new RowStyle(SizeType.AutoSize));
            col.RowStyles.Add(new RowStyle(SizeType.AutoSize));
            col.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
            var icon = new Label { Text = "⏳", Font = new Font("Segoe UI Emoji", 36), AutoSize = true, ForeColor = Color.White, BackColor = Color.Transparent, Anchor = AnchorStyles.None };
            title.Font = new Font("Segoe UI", 18, FontStyle.Bold);
            sub.Font = new Font("Segoe UI", 11);
            foreach (var l in new[] { title, sub })
            {
                l.AutoSize = true; l.MaximumSize = new Size(450, 0); l.ForeColor = Color.White;
                l.BackColor = Color.Transparent; l.Anchor = AnchorStyles.None; l.TextAlign = ContentAlignment.MiddleCenter;
                l.Margin = new Padding(0, 10, 0, 10);
            }
            options.FlowDirection = FlowDirection.TopDown;
            options.WrapContents = false;
            options.Dock = DockStyle.Fill;
            options.BackColor = Color.Transparent;
            options.Padding = new Padding(0, 14, 0, 0);
            col.Controls.Add(icon, 0, 0);
            col.Controls.Add(title, 0, 1);
            col.Controls.Add(sub, 0, 2);
            col.Controls.Add(options, 0, 3);
            Controls.Add(col);
            Look.ApplyFore(this);

            timer.Tick += (_, _) =>
            {
                secondsLeft--;
                if (secondsLeft > 0) { title.Text = secondsLeft.ToString(); return; }
                timer.Stop();
                title.Text = "C'est ton choix, il est respecté.";
                sub.Text = "« Fermer » arrête stop#app jusqu'au prochain démarrage de l'ordinateur.\n« Désinstaller » l'enlève complètement.";
                SetOptions(
                    ("Fermer stop#app", () => Finish("quit")),
                    ("Désinstaller stop#app", () => Finish("uninstall")),
                    ("Finalement, je le garde", () => Close()));
            };
            StepWhat();
        }

        protected override void OnPaintBackground(PaintEventArgs e) => Theme.Current.Paint(e.Graphics, ClientRectangle);

        void StepWhat()
        {
            title.Text = "Qu'est-ce que tu veux faire ?";
            sub.Text = "Tu essaies de fermer ou de désinstaller stop#app.";
            SetOptions(
                ("Modifier mes horaires, applis ou sites", () => { MessageBox.Show(this, "Dans stop#app : « Demander à modifier », puis 5 minutes d'attente.", "stop#app"); Close(); }),
                ("Faire une pause", () => { MessageBox.Show(this, "Utilise ta pause bonus de 10 minutes sur l'écran d'accueil de stop#app.", "stop#app"); Close(); }),
                ("Fermer ou désinstaller stop#app", StepWhy),
                ("Rien, je me suis trompé", () => Close()));
        }

        void StepWhy()
        {
            title.Text = "Pourquoi veux-tu arrêter ?";
            sub.Text = "Ta réponse aide à trouver une meilleure solution.";
            SetOptions(
                ("Il me bloque trop souvent", () => Advice("Tu peux ajouter des créneaux libres ou retirer des applis de la liste, sans tout arrêter.")),
                ("Il y a un bug", () => Advice("Une mise à jour peut corriger le problème, sans perdre tes progrès.")),
                ("Je n'en ai plus besoin", StepProgress),
                ("Autre raison", StepProgress));
        }

        void Advice(string text)
        {
            title.Text = "Une autre solution ?";
            sub.Text = text;
            SetOptions(("Ajuster mes réglages", () => Close()), ("Continuer quand même", StepProgress));
        }

        void StepProgress()
        {
            title.Text = "Regarde ce que tu as accompli";
            sub.Text = Texts.Pick(Texts.Quit) + "\n\n" +
                $"• {Rules.Streak()} jour(s) d'affilée\n• {Store.D.BlocksTotal} tentative(s) évitée(s)\n" +
                $"• environ {Rules.Duration(TimeSpan.FromMinutes(Store.D.BlocksTotal * 5))} de temps gagné";
            SetOptions(("Continuer quand même", StepWait));
        }

        void StepWait()
        {
            sub.Text = "Prends 30 secondes. Respire. Si tu veux toujours continuer après, tu pourras.";
            options.Controls.Clear();
            secondsLeft = 30;
            title.Text = "30";
            timer.Start();
        }

        void Finish(string r)
        {
            Result = r;
            DialogResult = DialogResult.OK;
            Close();
        }

        void SetOptions(params (string label, Action action)[] items)
        {
            options.Controls.Clear();
            foreach (var (label, action) in items)
            {
                var b = new Button
                {
                    Text = label,
                    Width = 440,
                    Height = 46,
                    FlatStyle = FlatStyle.Flat,
                    BackColor = Color.White,
                    ForeColor = Theme.Current.Accent,
                    Font = new Font("Segoe UI", 11, FontStyle.Bold),
                    Margin = new Padding(0, 0, 0, 10),
                    Cursor = Cursors.Hand,
                };
                b.FlatAppearance.BorderSize = 0;
                b.Click += (_, _) => action();
                options.Controls.Add(b);
            }
        }

        protected override void OnFormClosed(FormClosedEventArgs e)
        {
            timer.Stop();
            base.OnFormClosed(e);
        }
    }

    public static class AppIcon
    {
        static Icon cached;
        public static Icon Get()
        {
            if (cached != null) return cached;
            try { cached = Icon.ExtractAssociatedIcon(Environment.ProcessPath); } catch { }
            return cached ??= SystemIcons.Shield;
        }
    }
}
