namespace StopApp
{
    /// <summary>Écran plein écran affiché quand une appli ou un site est bloqué.</summary>
    public class BlockedForm : Form
    {
        readonly Label what = new(), quote = new(), idea = new(), count = new();

        public BlockedForm()
        {
            FormBorderStyle = FormBorderStyle.None;
            WindowState = FormWindowState.Maximized;
            TopMost = true;
            ShowInTaskbar = false;
            StartPosition = FormStartPosition.Manual;
            DoubleBuffered = true;
            KeyPreview = true;
            Text = "stop#app";
            Icon = AppIcon.Get();

            var col = new TableLayoutPanel
            {
                Dock = DockStyle.Fill,
                BackColor = Color.Transparent,
                ColumnCount = 1,
                RowCount = 9,
                Padding = new Padding(40),
            };
            col.RowStyles.Add(new RowStyle(SizeType.Percent, 50));
            for (int i = 0; i < 7; i++) col.RowStyles.Add(new RowStyle(SizeType.AutoSize));
            col.RowStyles.Add(new RowStyle(SizeType.Percent, 50));

            Label Make(string text, float size, FontStyle style, Color color)
            {
                var l = new Label
                {
                    Text = text,
                    AutoSize = true,
                    Anchor = AnchorStyles.None,
                    TextAlign = ContentAlignment.MiddleCenter,
                    ForeColor = color,
                    BackColor = Color.Transparent,
                    Font = new Font("Segoe UI", size, style),
                    Margin = new Padding(0, 8, 0, 8),
                    MaximumSize = new Size(900, 0),
                };
                return l;
            }

            var lockIcon = Make("🔒", 54, FontStyle.Regular, Color.White);
            lockIcon.Font = new Font("Segoe UI Emoji", 54);
            var title = Make("Bloqué par stop#app", 15, FontStyle.Bold, Color.FromArgb(230, 255, 255, 255));
            what.Font = new Font("Segoe UI", 34, FontStyle.Bold);
            Style(what, Color.White);
            quote.Font = new Font("Segoe UI", 15);
            Style(quote, Color.FromArgb(235, 255, 255, 255));
            idea.Font = new Font("Segoe UI Emoji", 15, FontStyle.Bold);
            Style(idea, Color.White);
            idea.Padding = new Padding(22, 14, 22, 14);
            idea.BackColor = Color.FromArgb(60, 255, 255, 255);
            count.Font = new Font("Segoe UI", 12);
            Style(count, Color.FromArgb(220, 255, 255, 255));

            var ok = new Button
            {
                Text = "OK, je m'y remets",
                AutoSize = true,
                Anchor = AnchorStyles.None,
                FlatStyle = FlatStyle.Flat,
                BackColor = Color.White,
                ForeColor = Theme.Current.Accent,
                Font = new Font("Segoe UI", 13, FontStyle.Bold),
                Padding = new Padding(28, 10, 28, 10),
                Margin = new Padding(0, 24, 0, 0),
                Cursor = Cursors.Hand,
            };
            ok.FlatAppearance.BorderSize = 0;
            ok.Click += (_, _) => Hide();

            col.Controls.Add(new Label { AutoSize = true }, 0, 0);
            col.Controls.Add(lockIcon, 0, 1);
            col.Controls.Add(title, 0, 2);
            col.Controls.Add(what, 0, 3);
            col.Controls.Add(quote, 0, 4);
            col.Controls.Add(idea, 0, 5);
            col.Controls.Add(count, 0, 6);
            col.Controls.Add(ok, 0, 7);
            Controls.Add(col);

            KeyDown += (_, e) => { if (e.KeyCode == Keys.Escape) Hide(); };
            FormClosing += (_, e) => { if (e.CloseReason == CloseReason.UserClosing) { e.Cancel = true; Hide(); } };
        }

        static void Style(Label l, Color c)
        {
            l.AutoSize = true;
            l.Anchor = AnchorStyles.None;
            l.TextAlign = ContentAlignment.MiddleCenter;
            l.ForeColor = c;
            l.BackColor = Color.Transparent;
            l.Margin = new Padding(0, 8, 0, 8);
            l.MaximumSize = new Size(900, 0);
        }

        protected override void OnPaintBackground(PaintEventArgs e) => Theme.Current.Paint(e.Graphics, ClientRectangle);

        public void ShowFor(string reason, string name)
        {
            what.Text = name;
            quote.Text = Rules.InFocus ? "Tu es en mode Focus. Termine ce que tu as commencé." : Texts.Pick(Texts.Blocked);
            idea.Text = "Et si tu faisais plutôt…  " + Texts.Pick(Texts.Ideas);
            var next = Rules.InFocus
                ? "Fin du Focus dans " + Rules.Duration(Store.D.FocusUntil - DateTime.Now)
                : Rules.NextFreeStart(DateTime.Now) is DateTime n ? "Prochaine pause à " + n.ToString("HH:mm") : "";
            count.Text = $"{reason} · Tu as résisté {Store.D.BlocksToday} fois aujourd'hui" + (next != "" ? "\n" + next : "");
            Look.ApplyFore(this);
            Invalidate(true);
            if (!Visible) Show();
            WindowState = FormWindowState.Maximized;
            TopMost = true;
            Activate();
            BringToFront();
        }
    }
}
