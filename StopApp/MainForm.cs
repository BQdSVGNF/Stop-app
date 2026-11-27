using System.Diagnostics;

namespace StopApp
{
    /// <summary>Fenêtre principale de stop#app pour Windows + le moteur de blocage.</summary>
    public class MainForm : Form
    {
        // ---------- Couleurs ----------
        // Le design choisi couvre toute la fenêtre (« Dégradé » garde le style classique clair)
        static bool Full => Look.Current != ArtStyle.GRADIENT;
        static bool DarkDesign => Full && !Look.Light;
        static readonly Color ClassicBg = ColorTranslator.FromHtml("#F5F3FB");
        static Color Bg => Full ? Color.Transparent : ClassicBg;
        static Color Card => !Full ? Color.White : Look.Current switch
        {
            ArtStyle.MINIMAL => Color.White,
            ArtStyle.SOFT3D => Color.FromArgb(215, 255, 255, 255),
            ArtStyle.CONTOUR => Color.FromArgb(235, 15, 42, 33),
            ArtStyle.BLUEPRINT => Color.FromArgb(235, 16, 32, 51),
            _ => Color.FromArgb(140, 0, 0, 0),
        };
        static Color Ink => DarkDesign ? Color.White : ColorTranslator.FromHtml("#1E1B2E");
        static Color InkSoft => DarkDesign ? ColorTranslator.FromHtml("#C8CCD8") : ColorTranslator.FromHtml("#6B6880");
        static readonly Color BtnInk = ColorTranslator.FromHtml("#1E1B2E");
        static readonly Color Soft = ColorTranslator.FromHtml("#ECEAFF");

        /// <summary>Processus jamais fermés (Windows lui-même, stop#app…).</summary>
        static readonly HashSet<string> Protected = new(StringComparer.OrdinalIgnoreCase)
        {
            "explorer", "svchost", "csrss", "winlogon", "wininit", "lsass", "services", "smss", "dwm", "system",
            "idle", "registry", "fontdrvhost", "sihost", "ctfmon", "taskhostw", "runtimebroker", "searchhost",
            "startmenuexperiencehost", "shellexperiencehost", "textinputhost", "securityhealthservice",
            "msmpeng", "stopapp", "conhost", "dllhost", "audiodg", "spoolsv", "lockapp", "logonui", "userinit",
        };

        readonly bool startHidden;
        readonly NotifyIcon tray = new();
        readonly System.Windows.Forms.Timer engine = new() { Interval = 1500 };
        readonly System.Windows.Forms.Timer ui = new() { Interval = 1000 };
        readonly BlockedForm blocked = new();
        bool reallyQuit;
        DateTime lastHostsCheck = DateTime.MinValue;
        DateTime lastBlockShown = DateTime.MinValue;
        readonly HashSet<string> notified = new();

        // ---------- Contrôles ----------
        GradientPanel header;
        Label lblStatus, lblStatusSub, lblHello, lblQuote, lblStats, lblLock, lblPass, lblEdit;
        Button btnStart, btnPass, btnEdit;
        readonly Panel[] pages = new Panel[5];
        readonly Button[] navButtons = new Button[5];
        ListBox lstWindows, lstApps, lstSites;
        ComboBox cboDay;
        DateTimePicker dtFrom, dtTo, dtVacStart, dtVacEnd;
        TextBox txtSite, txtApp;
        Label lblVac, lblAppsLock, lblSitesLock, lblTimesLock;
        FlowLayoutPanel themeBox, styleBox;

        public MainForm(bool hidden)
        {
            startHidden = hidden;
            Text = "stop#app";
            Icon = AppIcon.Get();
            ClientSize = new Size(560, 780);
            MinimumSize = new Size(520, 640);
            StartPosition = FormStartPosition.CenterScreen;
            BackColor = ClassicBg;
            Font = new Font("Segoe UI", 10);
            DoubleBuffered = true;

            BuildUi();
            BuildTray();

            engine.Tick += (_, _) => EngineTick();
            ui.Tick += (_, _) => RefreshUi();
            engine.Start();
            ui.Start();
            EngineTick();
            RefreshUi(true);

            FormClosing += (_, e) =>
            {
                // La croix ne ferme pas stop#app : il reste dans la barre des tâches (près de l'heure)
                if (!reallyQuit && e.CloseReason == CloseReason.UserClosing)
                {
                    e.Cancel = true;
                    Hide();
                    tray.ShowBalloonTip(3000, "stop#app continue", "stop#app reste actif près de l'horloge.", ToolTipIcon.Info);
                }
            };
        }

        protected override void SetVisibleCore(bool value)
        {
            // Lancé au démarrage de Windows : on reste discret, seulement l'icône près de l'heure
            if (startHidden && !IsHandleCreated) { CreateHandle(); value = false; }
            base.SetVisibleCore(value);
        }

        public void ShowMain()
        {
            Show();
            if (WindowState == FormWindowState.Minimized) WindowState = FormWindowState.Normal;
            Activate();
            BringToFront();
            RefreshUi(true);
        }

        // =====================================================================
        // Moteur : fermeture des applis bloquées + sites (fichier hosts)
        // =====================================================================

        void EngineTick()
        {
            Rules.Tick();
            bool blocking = Rules.IsBlocking;

            if (blocking && Store.D.BlockedApps.Count > 0)
            {
                var names = new HashSet<string>(Store.D.BlockedApps.Select(NormApp), StringComparer.OrdinalIgnoreCase);
                string hit = null;
                foreach (var p in Process.GetProcesses())
                {
                    try
                    {
                        if (names.Contains(p.ProcessName) && !Protected.Contains(p.ProcessName))
                        {
                            hit ??= Describe(p);
                            p.Kill(true);
                        }
                    }
                    catch { }
                    finally { p.Dispose(); }
                }
                if (hit != null && (DateTime.Now - lastBlockShown).TotalSeconds > 3)
                {
                    lastBlockShown = DateTime.Now;
                    Rules.AddBlock();
                    blocked.ShowFor(Rules.InFocus ? "Mode Focus en cours" : "Application bloquée", hit);
                }
            }

            // Sites : appliqués dès que l'état change, et revérifiés toutes les 20 s
            var sites = blocking ? Store.D.BlockedSites : new List<string>();
            if ((DateTime.Now - lastHostsCheck).TotalSeconds > 20 || hostsState != blocking || hostsCount != Store.D.BlockedSites.Count)
            {
                lastHostsCheck = DateTime.Now;
                hostsState = blocking;
                hostsCount = Store.D.BlockedSites.Count;
                Hosts.Apply(sites);
            }

            Prevention();
        }
        bool? hostsState;
        int hostsCount = -1;

        /// <summary>Petites notifications avant la fin d'une pause, du mode modification…</summary>
        void Prevention()
        {
            var now = DateTime.Now;
            void Once(string key, string title, string text)
            {
                if (!notified.Add(key + now.ToString("yyyyMMdd"))) return;
                tray.ShowBalloonTip(5000, title, text, ToolTipIcon.Info);
            }
            if (Store.D.Started && !Rules.InFocus && !Rules.InPass && !Rules.InEdit && Rules.CurrentFreeEnd(now) is DateTime end)
            {
                var left = end - now;
                if (left.TotalMinutes <= 5 && left.TotalMinutes > 0)
                    Once("end" + end.ToString("HHmm"), "Le blocage reprend bientôt", $"Plus que {Rules.Duration(left)} de pause. Termine ce que tu fais.");
            }
            if (Rules.InPass && (Store.D.PassUntil - now).TotalMinutes <= 1)
                Once("pass", "Pause bonus presque finie", "Encore une minute.");
            if (Rules.InEdit && (Store.D.EditUntil - now).TotalMinutes <= 1)
                Once("edit" + Store.D.EditUntil.ToString("HHmm"), "Mode modification", "Plus qu'une minute pour modifier tes réglages.");
        }

        static string NormApp(string n)
        {
            n = (n ?? "").Trim();
            if (n.EndsWith(".exe", StringComparison.OrdinalIgnoreCase)) n = n.Substring(0, n.Length - 4);
            return n;
        }

        /// <summary>Nom lisible d'un programme (« Discord » plutôt que « Discord.exe »).</summary>
        static string Describe(Process p)
        {
            try
            {
                var d = p.MainModule?.FileVersionInfo?.FileDescription;
                if (!string.IsNullOrWhiteSpace(d)) return d;
            }
            catch { }
            return p.ProcessName;
        }

        // =====================================================================
        // Interface
        // =====================================================================

        void BuildUi()
        {
            // ----- En-tête dégradé -----
            header = new GradientPanel { Dock = DockStyle.Top, Height = 170, Padding = new Padding(26, 20, 26, 16), SeeThrough = Full, BackColor = Full ? Color.Transparent : ClassicBg };
            lblHello = new Label { AutoSize = true, ForeColor = Color.FromArgb(235, 255, 255, 255), BackColor = Color.Transparent, Font = new Font("Segoe UI", 11), Location = new Point(26, 18) };
            var brand = new Label { Text = "stop#app", AutoSize = true, ForeColor = Color.White, BackColor = Color.Transparent, Font = new Font("Segoe UI", 11, FontStyle.Bold), Anchor = AnchorStyles.Top | AnchorStyles.Right };
            lblStatus = new Label { AutoSize = true, ForeColor = Color.White, BackColor = Color.Transparent, Font = new Font("Segoe UI Emoji", 22, FontStyle.Bold), Location = new Point(24, 52) };
            lblStatusSub = new Label { AutoSize = true, ForeColor = Color.FromArgb(235, 255, 255, 255), BackColor = Color.Transparent, Font = new Font("Segoe UI", 11), Location = new Point(28, 100), MaximumSize = new Size(500, 0) };
            header.Controls.AddRange(new Control[] { lblHello, brand, lblStatus, lblStatusSub });
            header.Resize += (_, _) => brand.Location = new Point(header.Width - brand.Width - 26, 18);

            // ----- Navigation -----
            var nav = new FlowLayoutPanel { Dock = DockStyle.Top, Height = 54, Padding = new Padding(16, 10, 16, 4), BackColor = Bg, WrapContents = false };
            string[] names = { "🏠 Accueil", "🕒 Horaires", "🧩 Applis", "🌐 Sites", "🎨 Thème" };
            for (int i = 0; i < names.Length; i++)
            {
                int idx = i;
                var b = new Button
                {
                    Text = names[i], AutoSize = true, FlatStyle = FlatStyle.Flat, Height = 36,
                    Font = new Font("Segoe UI Emoji", 9.5f, FontStyle.Bold), Margin = new Padding(0, 0, 6, 0),
                    Cursor = Cursors.Hand, Padding = new Padding(6, 0, 6, 0),
                };
                b.FlatAppearance.BorderSize = 0;
                b.Click += (_, _) => ShowPage(idx);
                navButtons[i] = b;
                nav.Controls.Add(b);
            }

            // ----- Pages -----
            var host = new Panel { Dock = DockStyle.Fill, BackColor = Bg };
            for (int i = 0; i < pages.Length; i++)
            {
                pages[i] = new Panel { Dock = DockStyle.Fill, AutoScroll = true, BackColor = Bg, Padding = new Padding(18, 6, 18, 18), Visible = i == 0 };
                // Le fond du design reste fixe : on redessine quand on fait défiler
                pages[i].Scroll += (o, _) => ((Control)o).Invalidate(true);
                pages[i].MouseWheel += (o, _) => ((Control)o).Invalidate(true);
                host.Controls.Add(pages[i]);
            }
            BuildHome(pages[0]);
            BuildTimes(pages[1]);
            BuildApps(pages[2]);
            BuildSites(pages[3]);
            BuildThemes(pages[4]);

            Controls.Add(host);
            Controls.Add(nav);
            Controls.Add(header);
            Look.ApplyFore(header);
            ShowPage(0);
        }

        void ShowPage(int idx)
        {
            for (int i = 0; i < pages.Length; i++)
            {
                pages[i].Visible = i == idx;
                navButtons[i].BackColor = i == idx ? Theme.Current.Accent : Soft;
                navButtons[i].ForeColor = i == idx ? Color.White : BtnInk;
            }
            RefreshUi(true);
        }

        // ----- Briques de mise en page -----

        FlowLayoutPanel Column(Panel page)
        {
            var f = new FlowLayoutPanel { Dock = DockStyle.Top, AutoSize = true, FlowDirection = FlowDirection.TopDown, WrapContents = false, BackColor = Bg };
            page.Controls.Add(f);
            return f;
        }

        Panel CardBox(FlowLayoutPanel col, string title, string subtitle = null)
        {
            var card = new FlowLayoutPanel
            {
                AutoSize = true, FlowDirection = FlowDirection.TopDown, WrapContents = false,
                BackColor = Card, Padding = new Padding(18, 14, 18, 16), Margin = new Padding(0, 10, 0, 0), Width = 500,
            };
            card.Controls.Add(new Label { Text = title, AutoSize = true, Font = new Font("Segoe UI Emoji", 13, FontStyle.Bold), ForeColor = Ink, Margin = new Padding(0, 0, 0, 2) });
            if (subtitle != null)
                card.Controls.Add(new Label { Text = subtitle, AutoSize = true, MaximumSize = new Size(460, 0), ForeColor = InkSoft, Margin = new Padding(0, 0, 0, 8) });
            col.Controls.Add(card);
            return card;
        }

        Button Btn(string text, bool primary = true, int width = 460)
        {
            var b = new Button
            {
                Text = text, Width = width, Height = 42, FlatStyle = FlatStyle.Flat, Cursor = Cursors.Hand,
                Font = new Font("Segoe UI Emoji", 10.5f, FontStyle.Bold), Margin = new Padding(0, 6, 6, 0),
                Tag = primary ? "primary" : "soft",
            };
            b.FlatAppearance.BorderSize = 0;
            Colorize(b);
            return b;
        }

        static void Colorize(Button b)
        {
            bool primary = (string)b.Tag == "primary";
            b.BackColor = !b.Enabled ? ColorTranslator.FromHtml("#E4E2EC") : primary ? Theme.Current.Accent : Soft;
            b.ForeColor = !b.Enabled ? ColorTranslator.FromHtml("#6B6880") : primary ? Color.White : BtnInk;
        }

        Label Note(string text = "")
        {
            return new Label { Text = text, AutoSize = true, MaximumSize = new Size(460, 0), ForeColor = InkSoft, Margin = new Padding(0, 6, 0, 0) };
        }

        Label LockLabel() => new Label
        {
            AutoSize = true, MaximumSize = new Size(460, 0), ForeColor = ColorTranslator.FromHtml("#B23A48"),
            Font = new Font("Segoe UI Emoji", 9.5f, FontStyle.Bold), Margin = new Padding(0, 6, 0, 0),
        };

        // ----- Accueil -----

        void BuildHome(Panel page)
        {
            var col = Column(page);

            var start = CardBox(col, "🛡️ Protection");
            btnStart = Btn("Démarrer la protection");
            btnStart.Click += (_, _) =>
            {
                if (MessageBox.Show(this, "Démarrer le blocage ? Ensuite, pour assouplir tes réglages, il faudra attendre une pause ou demander 5 minutes à l'avance.", "stop#app", MessageBoxButtons.YesNo, MessageBoxIcon.Question) != DialogResult.Yes) return;
                Store.D.Started = true; Store.Save(); EngineTick(); RefreshUi(true);
            };
            start.Controls.Add(btnStart);
            lblEdit = Note();
            start.Controls.Add(lblEdit);
            btnEdit = Btn("✏️ Demander à modifier (5 min)", false);
            btnEdit.Click += (_, _) => { Rules.RequestEdit(); RefreshUi(true); };
            start.Controls.Add(btnEdit);

            var stats = CardBox(col, "📊 Tes progrès");
            lblStats = new Label { AutoSize = true, Font = new Font("Segoe UI", 11), ForeColor = Ink, MaximumSize = new Size(460, 0) };
            stats.Controls.Add(lblStats);
            lblQuote = new Label { AutoSize = true, Font = new Font("Segoe UI", 10.5f, FontStyle.Italic), ForeColor = InkSoft, MaximumSize = new Size(460, 0), Margin = new Padding(0, 10, 0, 0), Cursor = Cursors.Hand, Text = Texts.Pick(Texts.Home) };
            lblQuote.Click += (_, _) => lblQuote.Text = Texts.Pick(Texts.Home);
            stats.Controls.Add(lblQuote);

            var focus = CardBox(col, "🎯 Mode Focus", "Bloque tes applis et sites, même pendant les pauses. Impossible d'arrêter avant la fin.");
            var row = new FlowLayoutPanel { AutoSize = true, WrapContents = false, Margin = new Padding(0) };
            foreach (var (label, min) in new[] { ("25 min", 25), ("1 h", 60), ("2 h", 120) })
            {
                var b = Btn(label, false, 146);
                b.Click += (_, _) =>
                {
                    if (MessageBox.Show(this, $"Lancer {label} de Focus ? Tu ne pourras pas l'arrêter avant la fin.", "Mode Focus", MessageBoxButtons.YesNo, MessageBoxIcon.Question) != DialogResult.Yes) return;
                    Rules.StartFocus(min); EngineTick(); RefreshUi(true);
                };
                row.Controls.Add(b);
            }
            focus.Controls.Add(row);

            var pass = CardBox(col, "☕ Pause bonus", "10 minutes sans blocage, une fois par jour.");
            btnPass = Btn("Prendre ma pause de 10 min", false);
            btnPass.Click += (_, _) =>
            {
                if (MessageBox.Show(this, "Utiliser ta pause bonus de 10 minutes ? Elle n'est disponible qu'une fois par jour.", "Pause bonus", MessageBoxButtons.YesNo) != DialogResult.Yes) return;
                Rules.StartPass(); EngineTick(); RefreshUi(true);
            };
            pass.Controls.Add(btnPass);
            lblPass = Note();
            pass.Controls.Add(lblPass);

            var quit = Btn("Fermer ou désinstaller stop#app…", false);
            quit.Margin = new Padding(0, 16, 0, 0);
            quit.Click += (_, _) => AskQuit();
            col.Controls.Add(quit);
            col.Controls.Add(Note("stop#app pour Windows · version 1.4"));
        }

        // ----- Horaires -----

        void BuildTimes(Panel page)
        {
            var col = Column(page);
            var card = CardBox(col, "🕒 Créneaux libres", "En dehors de ces créneaux, tes applis et sites bloqués sont fermés.");
            cboDay = new ComboBox { DropDownStyle = ComboBoxStyle.DropDownList, Width = 460, Font = new Font("Segoe UI", 10.5f) };
            cboDay.Items.AddRange(new object[] { "Lundi → vendredi", "Samedi et dimanche", "Vacances" });
            cboDay.SelectedIndex = Rules.IsVacation(DateTime.Now) ? 2 : Rules.IsWeekend(DateTime.Now) ? 1 : 0;
            cboDay.SelectedIndexChanged += (_, _) => FillWindows();
            card.Controls.Add(cboDay);
            lstWindows = new ListBox { Width = 460, Height = 110, Font = new Font("Segoe UI", 11), Margin = new Padding(0, 8, 0, 0), BorderStyle = BorderStyle.FixedSingle, BackColor = DarkDesign ? Color.FromArgb(20, 30, 28) : Color.White, ForeColor = Ink };
            card.Controls.Add(lstWindows);

            var row = new FlowLayoutPanel { AutoSize = true, WrapContents = false, Margin = new Padding(0, 8, 0, 0) };
            dtFrom = new DateTimePicker { Format = DateTimePickerFormat.Custom, CustomFormat = "HH:mm", ShowUpDown = true, Width = 90, Value = DateTime.Today.AddHours(15) };
            dtTo = new DateTimePicker { Format = DateTimePickerFormat.Custom, CustomFormat = "HH:mm", ShowUpDown = true, Width = 90, Value = DateTime.Today.AddHours(17) };
            row.Controls.Add(new Label { Text = "De", AutoSize = true, Margin = new Padding(0, 6, 4, 0) });
            row.Controls.Add(dtFrom);
            row.Controls.Add(new Label { Text = "à", AutoSize = true, Margin = new Padding(8, 6, 4, 0) });
            row.Controls.Add(dtTo);
            var add = Btn("Ajouter", true, 110); add.Margin = new Padding(10, 0, 0, 0); add.Height = 30;
            add.Click += (_, _) =>
            {
                if (!Rules.CanEdit) return;
                var w = $"{dtFrom.Value:HH:mm}-{dtTo.Value:HH:mm}";
                var list = CurrentList();
                if (!list.Contains(w)) { list.Add(w); list.Sort(StringComparer.Ordinal); Store.Save(); }
                FillWindows();
            };
            row.Controls.Add(add);
            card.Controls.Add(row);
            var del = Btn("Retirer le créneau sélectionné", false);
            del.Click += (_, _) =>
            {
                if (!Rules.CanEdit || lstWindows.SelectedItem == null) return;
                CurrentList().Remove(lstWindows.SelectedItem.ToString()); Store.Save(); FillWindows();
            };
            card.Controls.Add(del);
            lblTimesLock = LockLabel();
            card.Controls.Add(lblTimesLock);

            var vac = CardBox(col, "🏖️ Vacances", "Pendant tes vacances, ce sont les créneaux « Vacances » qui comptent (à la place de la semaine et du week-end).");
            var vrow = new FlowLayoutPanel { AutoSize = true, WrapContents = false };
            dtVacStart = new DateTimePicker { Format = DateTimePickerFormat.Short, Width = 130 };
            dtVacEnd = new DateTimePicker { Format = DateTimePickerFormat.Short, Width = 130 };
            vrow.Controls.Add(new Label { Text = "Du", AutoSize = true, Margin = new Padding(0, 6, 4, 0) });
            vrow.Controls.Add(dtVacStart);
            vrow.Controls.Add(new Label { Text = "au", AutoSize = true, Margin = new Padding(8, 6, 4, 0) });
            vrow.Controls.Add(dtVacEnd);
            vac.Controls.Add(vrow);
            var vbtns = new FlowLayoutPanel { AutoSize = true, WrapContents = false };
            var save = Btn("Enregistrer", true, 200);
            save.Click += (_, _) =>
            {
                if (!Rules.CanEdit) return;
                if (dtVacEnd.Value.Date < dtVacStart.Value.Date) { MessageBox.Show(this, "La fin doit être après le début.", "Vacances"); return; }
                Store.D.VacationStart = dtVacStart.Value.ToString("yyyy-MM-dd");
                Store.D.VacationEnd = dtVacEnd.Value.ToString("yyyy-MM-dd");
                Store.Save(); RefreshUi(true);
            };
            var clear = Btn("Effacer", false, 200);
            clear.Click += (_, _) =>
            {
                if (!Rules.CanEdit) return;
                Store.D.VacationStart = Store.D.VacationEnd = ""; Store.Save(); RefreshUi(true);
            };
            vbtns.Controls.Add(save);
            vbtns.Controls.Add(clear);
            vac.Controls.Add(vbtns);
            lblVac = Note();
            vac.Controls.Add(lblVac);
            FillWindows();
        }

        List<string> CurrentList() => cboDay.SelectedIndex switch
        {
            1 => Store.D.FreeWeekend,
            2 => Store.D.FreeVacation,
            _ => Store.D.FreeWeek,
        };

        void FillWindows()
        {
            lstWindows.Items.Clear();
            foreach (var w in CurrentList()) lstWindows.Items.Add(w);
        }

        // ----- Applis -----

        void BuildApps(Panel page)
        {
            var col = Column(page);
            var card = CardBox(col, "🧩 Applis bloquées", "Pendant le blocage, ces programmes sont fermés dès qu'ils s'ouvrent. Ajouter une appli est toujours possible ; en retirer une demande une pause ou le mode modification.");
            lstApps = new ListBox { Width = 460, Height = 170, Font = new Font("Segoe UI", 11), BorderStyle = BorderStyle.FixedSingle, BackColor = DarkDesign ? Color.FromArgb(20, 30, 28) : Color.White, ForeColor = Ink };
            card.Controls.Add(lstApps);
            var pick = Btn("➕ Choisir parmi les programmes ouverts");
            pick.Click += (_, _) => PickRunningApp();
            card.Controls.Add(pick);
            var row = new FlowLayoutPanel { AutoSize = true, WrapContents = false, Margin = new Padding(0, 8, 0, 0) };
            txtApp = new TextBox { Width = 300, Font = new Font("Segoe UI", 11), PlaceholderText = "ou un nom : Discord, steam, RobloxPlayerBeta…" };
            var add = Btn("Ajouter", false, 150); add.Height = 30; add.Margin = new Padding(8, 0, 0, 0);
            add.Click += (_, _) => AddApp(txtApp.Text);
            row.Controls.Add(txtApp);
            row.Controls.Add(add);
            card.Controls.Add(row);
            var del = Btn("Retirer l'appli sélectionnée", false);
            del.Click += (_, _) =>
            {
                if (!Rules.CanEdit || lstApps.SelectedItem == null) return;
                Store.D.BlockedApps.Remove(lstApps.SelectedItem.ToString()); Store.Save(); RefreshUi(true);
            };
            card.Controls.Add(del);
            lblAppsLock = LockLabel();
            card.Controls.Add(lblAppsLock);
        }

        void AddApp(string raw)
        {
            var n = NormApp(raw);
            if (n == "") return;
            if (Protected.Contains(n)) { MessageBox.Show(this, $"« {n} » fait partie de Windows : il ne peut pas être bloqué.", "stop#app"); return; }
            if (!Store.D.BlockedApps.Contains(n, StringComparer.OrdinalIgnoreCase)) { Store.D.BlockedApps.Add(n); Store.Save(); }
            txtApp.Text = "";
            EngineTick();
            RefreshUi(true);
        }

        void PickRunningApp()
        {
            var items = new SortedDictionary<string, string>(StringComparer.OrdinalIgnoreCase);
            foreach (var p in Process.GetProcesses())
            {
                try
                {
                    if (p.MainWindowHandle != IntPtr.Zero && !Protected.Contains(p.ProcessName) && !items.ContainsKey(p.ProcessName))
                    {
                        var title = string.IsNullOrWhiteSpace(p.MainWindowTitle) ? "" : " — " + p.MainWindowTitle;
                        if (title.Length > 60) title = title.Substring(0, 60) + "…";
                        items[p.ProcessName] = p.ProcessName + title;
                    }
                }
                catch { }
                finally { p.Dispose(); }
            }
            using var f = new Form { Text = "Programmes ouverts", ClientSize = new Size(460, 420), StartPosition = FormStartPosition.CenterParent, FormBorderStyle = FormBorderStyle.FixedDialog, MaximizeBox = false, MinimizeBox = false, Icon = AppIcon.Get() };
            var lst = new CheckedListBox { Dock = DockStyle.Fill, CheckOnClick = true, Font = new Font("Segoe UI", 10.5f) };
            foreach (var kv in items) lst.Items.Add(new KeyValuePair<string, string>(kv.Key, kv.Value));
            lst.DisplayMember = "Value";
            var ok = new Button { Text = "Bloquer la sélection", Dock = DockStyle.Bottom, Height = 44, FlatStyle = FlatStyle.Flat, BackColor = Theme.Current.Accent, ForeColor = Color.White, Font = new Font("Segoe UI", 10.5f, FontStyle.Bold) };
            ok.FlatAppearance.BorderSize = 0;
            ok.Click += (_, _) =>
            {
                foreach (var it in lst.CheckedItems) AddApp(((KeyValuePair<string, string>)it).Key);
                f.Close();
            };
            f.Controls.Add(lst);
            f.Controls.Add(ok);
            f.ShowDialog(this);
        }

        // ----- Sites -----

        void BuildSites(Panel page)
        {
            var col = Column(page);
            var card = CardBox(col, "🌐 Sites bloqués", "Bloqués dans tous les navigateurs (Chrome, Edge, Firefox…) pendant le blocage. Ajouter est toujours possible ; retirer demande une pause ou le mode modification.");
            lstSites = new ListBox { Width = 460, Height = 170, Font = new Font("Segoe UI", 11), BorderStyle = BorderStyle.FixedSingle, BackColor = DarkDesign ? Color.FromArgb(20, 30, 28) : Color.White, ForeColor = Ink };
            card.Controls.Add(lstSites);
            var row = new FlowLayoutPanel { AutoSize = true, WrapContents = false, Margin = new Padding(0, 8, 0, 0) };
            txtSite = new TextBox { Width = 300, Font = new Font("Segoe UI", 11), PlaceholderText = "ex. tiktok.com" };
            var add = Btn("Ajouter", true, 150); add.Height = 30; add.Margin = new Padding(8, 0, 0, 0);
            add.Click += (_, _) =>
            {
                var s = Hosts.Normalize(txtSite.Text);
                if (s == "") { MessageBox.Show(this, "Écris une adresse comme « tiktok.com ».", "stop#app"); return; }
                if (!Store.D.BlockedSites.Contains(s)) { Store.D.BlockedSites.Add(s); Store.Save(); }
                txtSite.Text = "";
                EngineTick(); RefreshUi(true);
            };
            row.Controls.Add(txtSite);
            row.Controls.Add(add);
            card.Controls.Add(row);
            var del = Btn("Retirer le site sélectionné", false);
            del.Click += (_, _) =>
            {
                if (!Rules.CanEdit || lstSites.SelectedItem == null) return;
                Store.D.BlockedSites.Remove(lstSites.SelectedItem.ToString()); Store.Save(); EngineTick(); RefreshUi(true);
            };
            card.Controls.Add(del);
            lblSitesLock = LockLabel();
            card.Controls.Add(lblSitesLock);
            card.Controls.Add(Note("Astuce : si un site s'ouvre encore, ferme et rouvre le navigateur une fois."));
        }

        // ----- Thèmes -----

        void BuildThemes(Panel page)
        {
            var col = Column(page);
            var card = CardBox(col, "🎨 Apparence", "Le design et les couleurs de l'en-tête, de l'écran de blocage et du questionnaire.");
            card.Controls.Add(new Label { Text = "Design", AutoSize = true, Font = new Font("Segoe UI", 10.5f, FontStyle.Bold), ForeColor = InkSoft, Margin = new Padding(0, 4, 0, 6) });
            styleBox = new FlowLayoutPanel { AutoSize = true, Width = 460, WrapContents = true };
            card.Controls.Add(styleBox);
            card.Controls.Add(new Label { Text = "Couleurs", AutoSize = true, Font = new Font("Segoe UI", 10.5f, FontStyle.Bold), ForeColor = InkSoft, Margin = new Padding(0, 10, 0, 6) });
            themeBox = new FlowLayoutPanel { AutoSize = true, Width = 460, WrapContents = true };
            card.Controls.Add(themeBox);
            FillThemes();
        }

        void FillThemes()
        {
            // ----- Designs : aperçus à cliquer -----
            foreach (Control old in styleBox.Controls) (old as PictureBox)?.Image?.Dispose();
            styleBox.Controls.Clear();
            foreach (var (style, label) in Look.Styles)
            {
                var st = style;
                var pic = new PictureBox
                {
                    Width = 140, Height = 84, Margin = new Padding(0, 0, 10, 10), Cursor = Cursors.Hand,
                    Image = Art.Preview(140, 84, Theme.Current, st),
                };
                pic.Paint += (_, e) =>
                {
                    var fore = (st == ArtStyle.SOFT3D || st == ArtStyle.MINIMAL) ? Look.Ink : Color.White;
                    TextRenderer.DrawText(e.Graphics, label, new Font("Segoe UI", 10.5f, FontStyle.Bold), new Rectangle(10, 54, 130, 26), fore, TextFormatFlags.Left);
                    if (st == Look.Current)
                    {
                        using var pen = new Pen(Ink, 4);
                        e.Graphics.DrawRectangle(pen, 2, 2, pic.Width - 4, pic.Height - 4);
                        TextRenderer.DrawText(e.Graphics, "✓", new Font("Segoe UI", 14, FontStyle.Bold), new Point(pic.Width - 30, 6), fore);
                    }
                };
                pic.Click += (_, _) =>
                {
                    Store.D.Style = st.ToString(); Store.Save();
                    BeginInvoke(new Action(ApplyLook));   // après le clic (le bouton va être recréé)
                };
                styleBox.Controls.Add(pic);
            }

            themeBox.Controls.Clear();
            foreach (var t in Theme.All)
            {
                var sw = new Panel { Width = 140, Height = 84, Margin = new Padding(0, 0, 10, 10), Cursor = Cursors.Hand };
                var theme = t;
                sw.Paint += (_, e) =>
                {
                    theme.PaintGradient(e.Graphics, sw.ClientRectangle);
                    TextRenderer.DrawText(e.Graphics, theme.Name, new Font("Segoe UI", 11, FontStyle.Bold), new Rectangle(10, 50, 130, 30), Color.White, TextFormatFlags.Left);
                    if (theme.Id == Theme.Current.Id)
                    {
                        using var pen = new Pen(Ink, 4);
                        e.Graphics.DrawRectangle(pen, 2, 2, sw.Width - 4, sw.Height - 4);
                        TextRenderer.DrawText(e.Graphics, "✓", new Font("Segoe UI", 14, FontStyle.Bold), new Point(sw.Width - 30, 6), Color.White);
                    }
                };
                sw.Click += (_, _) =>
                {
                    Store.D.Theme = theme.Id; Store.Save();
                    BeginInvoke(new Action(ApplyLook));   // après le clic (le bouton va être recréé)
                };
                themeBox.Controls.Add(sw);
            }
        }

        /// <summary>Applique le design et les couleurs choisis partout.</summary>
        void ApplyLook()
        {
            // Toute la fenêtre est reconstruite dans le nouveau design
            SuspendLayout();
            var old = Controls.Cast<Control>().ToList();
            Controls.Clear();
            foreach (var c in old) c.Dispose();
            BuildUi();
            ResumeLayout(true);
            Look.ApplyFore(blocked);
            blocked.Invalidate(true);
            ShowPage(4);
            RefreshUi(true);
            Invalidate(true);
        }

        protected override void OnPaintBackground(PaintEventArgs e)
        {
            if (Full) Theme.Current.Paint(e.Graphics, ClientRectangle);
            else base.OnPaintBackground(e);
        }

        protected override void OnResize(EventArgs e)
        {
            base.OnResize(e);
            Invalidate(true);
        }

        // =====================================================================
        // Rafraîchissement de l'écran
        // =====================================================================

        string lastListsKey = "";

        void RefreshUi(bool force = false)
        {
            if (!force && !Visible) return;
            var now = DateTime.Now;
            var d = Store.D;
            lblHello.Text = now.Hour is >= 5 and < 12 ? "Bonjour" : now.Hour is >= 12 and < 18 ? "Bon après-midi" : "Bonsoir";

            string status, sub;
            if (Rules.InFocus) { status = "🎯 Mode Focus"; sub = "Encore " + Rules.Duration(d.FocusUntil - now) + ". Tout est bloqué, même pendant les pauses."; }
            else if (!d.Started) { status = "⏸ Pas encore démarré"; sub = "Choisis tes applis et tes sites, puis démarre la protection."; }
            else if (Rules.InPass) { status = "☕ Pause bonus"; sub = "Le blocage reprend dans " + Rules.Duration(d.PassUntil - now) + "."; }
            else if (Rules.InEdit) { status = "✏️ Mode modification"; sub = "Tu peux modifier tes réglages encore " + Rules.Duration(d.EditUntil - now) + "."; }
            else if (Rules.CurrentFreeEnd(now) is DateTime end) { status = "🔓 Pause libre"; sub = "Jusqu'à " + end.ToString("HH:mm") + (Rules.IsVacation(now) ? " · vacances" : ""); }
            else
            {
                status = "🔒 Blocage actif";
                sub = Rules.NextFreeStart(now) is DateTime n
                    ? "Prochaine pause à " + n.ToString("HH:mm") + (n.Date > now.Date ? " (" + n.ToString("dddd") + ")" : "") + " · dans " + Rules.Duration(n - now)
                    : "Aucun créneau libre prévu.";
            }
            lblStatus.Text = status;
            lblStatusSub.Text = sub;
            var tip = "stop#app · " + status.Substring(status.IndexOf(' ') + 1);
            tray.Text = tip.Length > 63 ? tip.Substring(0, 63) : tip;

            btnStart.Visible = !d.Started;
            bool canEdit = Rules.CanEdit;
            btnEdit.Visible = d.Started && !canEdit && !Rules.InFocus;
            btnEdit.Enabled = !Rules.EditPending;
            btnEdit.Text = Rules.EditPending ? "⏳ Modification possible dans " + Rules.Duration(Rules.EditWaitLeft) : "✏️ Demander à modifier (5 min)";
            Colorize(btnEdit);
            lblEdit.Text = !d.Started ? "" : canEdit ? "✅ Tu peux modifier tes réglages en ce moment." : Rules.InFocus ? "🎯 Réglages verrouillés pendant le Focus." : "🔒 Réglages verrouillés. Demande à modifier : 5 minutes d'attente, puis 10 minutes pour changer.";

            lblStats.Text = $"Aujourd'hui : {d.BlocksToday} tentative(s) bloquée(s)\nSérie : {Rules.Streak()} jour(s) d'affilée\nTotal : {d.BlocksTotal} · environ {Rules.Duration(TimeSpan.FromMinutes(d.BlocksTotal * 5))} gagnées";

            btnPass.Enabled = d.Started && !Rules.PassUsedToday && Rules.IsBlocking && !Rules.InFocus;
            Colorize(btnPass);
            lblPass.Text = Rules.InPass ? "En cours, fin à " + d.PassUntil.ToString("HH:mm") : Rules.PassUsedToday ? "Déjà utilisée aujourd'hui. À demain !" : "";

            string lockText = canEdit ? "" : "🔒 Retirer est verrouillé pendant le blocage (demande à modifier sur l'accueil).";
            lblAppsLock.Text = lblSitesLock.Text = lockText;
            lblTimesLock.Text = canEdit ? "" : "🔒 Horaires verrouillés pendant le blocage (demande à modifier sur l'accueil).";
            lblVac.Text = d.VacationStart != "" ? $"Vacances enregistrées : du {d.VacationStart} au {d.VacationEnd}" + (Rules.IsVacation(now) ? " · en cours" : "") : "Aucunes vacances enregistrées.";

            var key = string.Join("|", d.BlockedApps) + "#" + string.Join("|", d.BlockedSites);
            if (force || key != lastListsKey)
            {
                lastListsKey = key;
                lstApps.Items.Clear(); foreach (var a in d.BlockedApps) lstApps.Items.Add(a);
                lstSites.Items.Clear(); foreach (var s in d.BlockedSites) lstSites.Items.Add(s);
            }
            header.Invalidate();
        }

        // =====================================================================
        // Icône près de l'horloge + fermeture
        // =====================================================================

        void BuildTray()
        {
            tray.Icon = AppIcon.Get();
            tray.Text = "stop#app";
            tray.Visible = true;
            var menu = new ContextMenuStrip();
            menu.Items.Add("Ouvrir stop#app", null, (_, _) => ShowMain());
            menu.Items.Add("Pause bonus (10 min)", null, (_, _) => { ShowMain(); ShowPage(0); });
            menu.Items.Add(new ToolStripSeparator());
            menu.Items.Add("Fermer stop#app…", null, (_, _) => AskQuit());
            tray.ContextMenuStrip = menu;
            tray.DoubleClick += (_, _) => ShowMain();
        }

        void AskQuit()
        {
            if (Rules.InFocus)
            {
                MessageBox.Show(this, "Impossible pendant le mode Focus. Encore " + Rules.Duration(Store.D.FocusUntil - DateTime.Now) + ".", "stop#app");
                return;
            }
            using var q = new QuitForm();
            if (q.ShowDialog(this) != DialogResult.OK) return;
            reallyQuit = true;
            Hosts.Apply(Array.Empty<string>());
            AutoStart.Unregister();
            if (q.Result == "uninstall")
            {
                try
                {
                    // Supprime le dossier d'installation et les réglages quelques secondes après la fermeture
                    var dir = Path.GetDirectoryName(Environment.ProcessPath);
                    var lnk1 = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.CommonDesktopDirectory), "stop#app.lnk");
                    var lnk2 = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.CommonPrograms), "stop#app.lnk");
                    Process.Start(new ProcessStartInfo("cmd.exe",
                        $"/c timeout /t 3 /nobreak >nul & rmdir /s /q \"{dir}\" & rmdir /s /q \"{Store.Dir}\" & del /f /q \"{lnk1}\" \"{lnk2}\"")
                    { CreateNoWindow = true, UseShellExecute = false, WindowStyle = ProcessWindowStyle.Hidden });
                }
                catch { }
                MessageBox.Show(this, "stop#app est désinstallé. Prends soin de toi !", "stop#app");
            }
            else
            {
                MessageBox.Show(this, "stop#app est fermé jusqu'au prochain démarrage de l'ordinateur.", "stop#app");
                // Au prochain démarrage, il revient : on remet la tâche de démarrage seulement
                AutoStart.RegisterLogonOnly(Environment.ProcessPath);
            }
            tray.Visible = false;
            Application.Exit();
        }
    }
}
