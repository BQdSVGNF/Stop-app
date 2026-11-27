using System.Globalization;
using System.Text.Json;

namespace StopApp
{
    /// <summary>Tous les réglages et compteurs de stop#app, enregistrés dans C:\ProgramData\stopapp.</summary>
    public class Data
    {
        // Créneaux libres (sans blocage) : "HH:mm-HH:mm"
        public List<string> FreeWeek { get; set; } = new() { "06:00-07:55", "15:00-17:30" };
        public List<string> FreeWeekend { get; set; } = new() { "10:00-12:00", "15:00-19:00" };
        public List<string> FreeVacation { get; set; } = new() { "09:00-23:00" };
        public string VacationStart { get; set; } = ""; // yyyy-MM-dd
        public string VacationEnd { get; set; } = "";

        public List<string> BlockedApps { get; set; } = new();
        public List<string> BlockedSites { get; set; } = new();

        public bool Started { get; set; }
        public DateTime EditRequestedAt { get; set; } = DateTime.MinValue;
        public DateTime EditUntil { get; set; } = DateTime.MinValue;
        public string PassDay { get; set; } = "";
        public DateTime PassUntil { get; set; } = DateTime.MinValue;
        public DateTime FocusUntil { get; set; } = DateTime.MinValue;

        public string StatsDay { get; set; } = "";
        public int BlocksToday { get; set; }
        public int BlocksTotal { get; set; }
        public List<string> ActiveDays { get; set; } = new();

        public string Theme { get; set; } = "corail";
        public string Style { get; set; } = "GRADIENT";
    }

    public static class Store
    {
        public static readonly string Dir = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData), "stopapp");
        static string FilePath => Path.Combine(Dir, "settings.json");

        public static Data D = Load();

        static Data Load()
        {
            try
            {
                if (File.Exists(FilePath))
                    return JsonSerializer.Deserialize<Data>(File.ReadAllText(FilePath)) ?? new Data();
            }
            catch { }
            return new Data();
        }

        public static void Save()
        {
            try
            {
                Directory.CreateDirectory(Dir);
                File.WriteAllText(FilePath, JsonSerializer.Serialize(D, new JsonSerializerOptions { WriteIndented = true }));
            }
            catch { }
        }
    }

    /// <summary>Les règles : mêmes horaires et mêmes délais que sur le téléphone.</summary>
    public static class Rules
    {
        public const int WaitMinutes = 5;   // attente avant de pouvoir modifier
        public const int EditMinutes = 10;  // durée du mode modification
        public const int PassMinutes = 10;  // pause bonus, une fois par jour

        static Data D => Store.D;
        static string Day(DateTime d) => d.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture);
        public static string Today => Day(DateTime.Now);

        public static bool IsWeekend(DateTime d) => d.DayOfWeek == DayOfWeek.Saturday || d.DayOfWeek == DayOfWeek.Sunday;

        public static bool IsVacation(DateTime d)
        {
            if (!DateTime.TryParseExact(D.VacationStart, "yyyy-MM-dd", CultureInfo.InvariantCulture, DateTimeStyles.None, out var s)) return false;
            if (!DateTime.TryParseExact(D.VacationEnd, "yyyy-MM-dd", CultureInfo.InvariantCulture, DateTimeStyles.None, out var e)) return false;
            return d.Date >= s.Date && d.Date <= e.Date;
        }

        /// <summary>Vacances > week-end > semaine.</summary>
        public static List<string> WindowsFor(DateTime d) =>
            IsVacation(d) ? D.FreeVacation : IsWeekend(d) ? D.FreeWeekend : D.FreeWeek;

        public static bool TryParse(string w, out int start, out int end)
        {
            start = end = 0;
            var parts = (w ?? "").Split('-');
            if (parts.Length != 2) return false;
            return TryMinutes(parts[0], out start) && TryMinutes(parts[1], out end);
        }

        static bool TryMinutes(string t, out int m)
        {
            m = 0;
            var p = t.Trim().Split(':');
            if (p.Length != 2 || !int.TryParse(p[0], out var h) || !int.TryParse(p[1], out var mi)) return false;
            m = h * 60 + mi;
            return h is >= 0 and < 24 && mi is >= 0 and < 60;
        }

        /// <summary>Le créneau libre en cours, ou null.</summary>
        public static string CurrentFree(DateTime now)
        {
            int m = now.Hour * 60 + now.Minute;
            foreach (var w in WindowsFor(now))
            {
                if (!TryParse(w, out var s, out var e)) continue;
                if (s <= e ? (m >= s && m < e) : m >= s) return w;
            }
            // Créneau d'hier qui passe minuit
            foreach (var w in WindowsFor(now.AddDays(-1)))
            {
                if (TryParse(w, out var s, out var e) && s > e && m < e) return w;
            }
            return null;
        }

        /// <summary>Fin du créneau libre en cours.</summary>
        public static DateTime? CurrentFreeEnd(DateTime now)
        {
            var w = CurrentFree(now);
            if (w == null || !TryParse(w, out var s, out var e)) return null;
            var end = now.Date.AddMinutes(e);
            if (end <= now) end = end.AddDays(1);
            return end;
        }

        /// <summary>Début du prochain créneau libre.</summary>
        public static DateTime? NextFreeStart(DateTime now)
        {
            DateTime? best = null;
            for (int i = 0; i < 8; i++)
            {
                var day = now.Date.AddDays(i);
                foreach (var w in WindowsFor(day))
                {
                    if (!TryParse(w, out var s, out _)) continue;
                    var start = day.AddMinutes(s);
                    if (start > now && (best == null || start < best)) best = start;
                }
            }
            return best;
        }

        public static bool InEdit => DateTime.Now < D.EditUntil;
        public static bool InPass => DateTime.Now < D.PassUntil;
        public static bool InFocus => DateTime.Now < D.FocusUntil;
        public static bool PassUsedToday => D.PassDay == Today;
        public static bool EditPending => D.EditRequestedAt != DateTime.MinValue;

        public static bool IsBlocking =>
            InFocus || (D.Started && CurrentFree(DateTime.Now) == null && !InEdit && !InPass);

        /// <summary>Peut-on assouplir les réglages en ce moment ?</summary>
        public static bool CanEdit =>
            !InFocus && (!D.Started || CurrentFree(DateTime.Now) != null || InEdit);

        public static void RequestEdit()
        {
            if (EditPending || CanEdit) return;
            D.EditRequestedAt = DateTime.Now;
            Store.Save();
        }

        /// <summary>Passe en mode modification une fois les 5 minutes d'attente écoulées.</summary>
        public static void Tick()
        {
            var now = DateTime.Now;
            if (EditPending && now >= D.EditRequestedAt.AddMinutes(WaitMinutes))
            {
                D.EditUntil = D.EditRequestedAt.AddMinutes(WaitMinutes + EditMinutes);
                D.EditRequestedAt = DateTime.MinValue;
                Store.Save();
            }
            RollDay();
            if (D.Started && !D.ActiveDays.Contains(Today))
            {
                D.ActiveDays.Add(Today);
                if (D.ActiveDays.Count > 400) D.ActiveDays.RemoveAt(0);
                Store.Save();
            }
        }

        public static TimeSpan EditWaitLeft =>
            EditPending ? D.EditRequestedAt.AddMinutes(WaitMinutes) - DateTime.Now : TimeSpan.Zero;

        public static void StartPass()
        {
            if (PassUsedToday) return;
            D.PassDay = Today;
            D.PassUntil = DateTime.Now.AddMinutes(PassMinutes);
            Store.Save();
        }

        public static void StartFocus(int minutes)
        {
            D.FocusUntil = DateTime.Now.AddMinutes(minutes);
            Store.Save();
        }

        static void RollDay()
        {
            if (D.StatsDay == Today) return;
            D.StatsDay = Today;
            D.BlocksToday = 0;
            Store.Save();
        }

        public static void AddBlock()
        {
            RollDay();
            D.BlocksToday++;
            D.BlocksTotal++;
            Store.Save();
        }

        /// <summary>Jours consécutifs (jusqu'à aujourd'hui) où la protection tournait.</summary>
        public static int Streak()
        {
            int n = 0;
            var d = DateTime.Now.Date;
            while (D.ActiveDays.Contains(Day(d))) { n++; d = d.AddDays(-1); }
            return n;
        }

        public static string Duration(TimeSpan t)
        {
            if (t < TimeSpan.Zero) t = TimeSpan.Zero;
            int m = (int)Math.Ceiling(t.TotalMinutes);
            return m < 60 ? $"{m} min" : $"{m / 60} h {m % 60:00}";
        }
    }
}
