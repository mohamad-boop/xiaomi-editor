package app.xeditor.picker

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

sealed class PickerResult {
    data class FromBitmap(val bitmap: Bitmap) : PickerResult()
    data class FromUri(val uri: Uri) : PickerResult()
    data object None : PickerResult()
}

class IconPickerContract(private val packageProvider: () -> String) :
    ActivityResultContract<Unit, PickerResult>() {

    override fun createIntent(context: Context, input: Unit): Intent {
        val pkg = packageProvider()
        val pm: PackageManager = context.packageManager

        // CandyBar / most modern packs put themselves in single-icon-pick mode
        // when launched with ACTION_PICK. The THEMES actions usually open the
        // dashboard ("apply whole pack" UI), which is the wrong UX here.
        val attempts = listOf(
            Intent("org.adw.launcher.icons.ACTION_PICK_ICON").setPackage(pkg),
            Intent(Intent.ACTION_PICK).setPackage(pkg).addCategory(Intent.CATEGORY_DEFAULT),
            Intent(Intent.ACTION_GET_CONTENT).setPackage(pkg).setType("image/*"),
            Intent("com.novalauncher.THEMES").addCategory(Intent.CATEGORY_DEFAULT).setPackage(pkg),
            Intent("org.adw.launcher.THEMES").addCategory(Intent.CATEGORY_DEFAULT).setPackage(pkg),
        )
        for (intent in attempts) {
            if (pm.resolveActivity(intent, 0) != null) return intent
        }
        return Intent(Intent.ACTION_MAIN).setPackage(pkg)
    }

    @Suppress("DEPRECATION")
    override fun parseResult(resultCode: Int, intent: Intent?): PickerResult {
        if (resultCode != android.app.Activity.RESULT_OK || intent == null) return PickerResult.None
        intent.getParcelableExtra<Bitmap>("icon")?.let { return PickerResult.FromBitmap(it) }
        intent.data?.let { return PickerResult.FromUri(it) }
        return PickerResult.None
    }
}
