using System.Diagnostics;

namespace StopApp
{
    internal static class Program
    {
        const string MutexName = "Global\\stopapp-windows-single";
        const string ShowEventName = "Global\\stopapp-windows-show";

        static string InstallDir => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "stopapp");
        static string InstalledExe => Path.Combine(InstallDir, "StopApp.exe");

        [STAThread]
        static void Main(string[] args)
        {
            bool background = args.Contains("--background");
            var me = Environment.ProcessPath ?? "";

            Application.SetHighDpiMode(HighDpiMode.PerMonitorV2);
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            // 1) Lancé depuis le .zip (Téléchargements, Bureau…) : on s'installe dans
            //    C:\Program Files\stopapp (installation ou mise à jour), puis on lance la copie installée.
            if (!string.Equals(Path.GetFullPath(me), Path.GetFullPath(InstalledExe), StringComparison.OrdinalIgnoreCase))
            {
                try
                {
                    Directory.CreateDirectory(InstallDir);
                    // Mise à jour : on arrête l'ancienne version le temps de la remplacer
                    foreach (var p in Process.GetProcessesByName("StopApp"))
                    {
                        try { if (p.Id != Environment.ProcessId) { p.Kill(); p.WaitForExit(5000); } } catch { }
                        finally { p.Dispose(); }
                    }
                    File.Copy(me, InstalledExe, true);
                    AutoStart.Register(InstalledExe);
                    CreateShortcuts();
                    Process.Start(new ProcessStartInfo(InstalledExe) { UseShellExecute = true });
                }
                catch (Exception ex)
                {
                    MessageBox.Show("Installation impossible : " + ex.Message, "stop#app", MessageBoxButtons.OK, MessageBoxIcon.Error);
                }
                return;
            }

            // 2) Une seule copie à la fois. Si stop#app tourne déjà, on affiche sa fenêtre.
            using var mutex = new Mutex(true, MutexName, out bool first);
            if (!first)
            {
                if (!background)
                {
                    try { using var ev = EventWaitHandle.OpenExisting(ShowEventName); ev.Set(); } catch { }
                }
                return;
            }

            AutoStart.Register(InstalledExe);
            using var showEvent = new EventWaitHandle(false, EventResetMode.AutoReset, ShowEventName);
            var form = new MainForm(background);

            // Attend les demandes « affiche-toi » (double-clic sur l'icône du bureau, etc.)
            var waiter = new Thread(() =>
            {
                while (true)
                {
                    try
                    {
                        showEvent.WaitOne();
                        if (form.IsDisposed) return;
                        form.BeginInvoke(new Action(form.ShowMain));
                    }
                    catch { return; }
                }
            }) { IsBackground = true };
            waiter.Start();

            Application.Run(form);
            GC.KeepAlive(mutex);
        }

        /// <summary>Raccourcis « stop#app » sur le Bureau et dans le menu Démarrer.</summary>
        static void CreateShortcuts()
        {
            var places = new[]
            {
                Environment.GetFolderPath(Environment.SpecialFolder.CommonDesktopDirectory),
                Environment.GetFolderPath(Environment.SpecialFolder.CommonPrograms),
            };
            foreach (var dir in places)
            {
                if (string.IsNullOrEmpty(dir)) continue;
                var lnk = Path.Combine(dir, "stop#app.lnk").Replace("'", "''");
                var target = InstalledExe.Replace("'", "''");
                Shell.Run("powershell", "-NoProfile -ExecutionPolicy Bypass -Command \"" +
                    $"$s=(New-Object -ComObject WScript.Shell).CreateShortcut('{lnk}'); $s.TargetPath='{target}'; $s.WorkingDirectory='{InstallDir.Replace("'", "''")}'; $s.Save()\"");
            }
        }
    }
}
