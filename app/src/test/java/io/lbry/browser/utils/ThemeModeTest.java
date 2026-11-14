package io.lbry.browser.utils;
import org.junit.Test;
import androidx.appcompat.app.AppCompatDelegate;
import static org.junit.Assert.*;
public class ThemeModeTest {
    @Test public void newInstallFollowsSystemAndExistingChoicesSurvive() {
        assertEquals("system", ThemeMode.resolve(null, null));
        assertEquals("dark", ThemeMode.resolve(null, true));
        assertEquals("light", ThemeMode.resolve(null, false));
        assertEquals("system", ThemeMode.resolve("system", true));
        assertEquals("light", ThemeMode.resolve("light", true));
        assertEquals("dark", ThemeMode.resolve("dark", false));
    }
    @Test public void modesAndUnknownValuesResolveSafely() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, ThemeMode.nightMode("dark"));
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, ThemeMode.nightMode("light"));
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, ThemeMode.nightMode("system"));
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, ThemeMode.nightMode("unknown"));
    }
}
