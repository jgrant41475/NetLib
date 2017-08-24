package local.john.netlib

import android.os.Bundle
import android.support.v4.app.NavUtils
import android.support.v4.app.TaskStackBuilder
import android.view.MenuItem
import local.john.netlib.Util.AppCompatPreferenceActivity

internal class SettingsActivity : AppCompatPreferenceActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        addPreferencesFromResource(R.xml.settings)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId) {
            android.R.id.home -> {
                val upIntent = NavUtils.getParentActivityIntent(this)
                if(NavUtils.shouldUpRecreateTask(this, upIntent))
                    TaskStackBuilder.create(this).addNextIntentWithParentStack(upIntent).startActivities()

                else
                    NavUtils.navigateUpTo(this, upIntent)

                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
