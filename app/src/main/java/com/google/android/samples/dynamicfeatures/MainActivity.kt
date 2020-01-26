/*
 * Copyright 2018 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.samples.dynamicfeatures

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.Group
import com.google.android.play.core.splitinstall.*
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus

private const val packageName = "com.google.android.samples.dynamicfeatures.ondemand"
private const val kotlinSampleClassname = "$packageName.KotlinSampleActivity"
private const val javaSampleClassname = "$packageName.JavaSampleActivity"
private const val nativeSampleClassname = "$packageName.NativeSampleActivity"

/** Activity that displays buttons and handles loading of feature modules. */
class MainActivity : AppCompatActivity() {

    private lateinit var progress: Group
    private lateinit var buttons: Group
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var manager: SplitInstallManager

    private val clickListener by lazy {
        View.OnClickListener {
            when (it.id) {
                R.id.btn_load_kotlin -> loadAndLaunchModule(moduleKotlin)
                R.id.btn_load_java -> loadAndLaunchModule(moduleJava)
                R.id.btn_load_native -> loadAndLaunchModule(moduleNative)
                R.id.btn_load_assets -> loadAndLaunchModule(moduleAssets)
                R.id.btn_install_all_now -> installAllFeaturesNow()
                R.id.btn_install_all_deferred -> installAllFeaturesDeferred()
                R.id.btn_request_uninstall -> requestUninstall()
            }
        }
    }

    private val installListener = SplitInstallStateUpdatedListener { state ->
        val multiInstall = state.moduleNames().size > 1
        val names = state.moduleNames().joinToString(" - " )
        when (state.status()) {
            SplitInstallSessionStatus.DOWNLOADING -> {
                displayLoadingState(state, "Downloading $names")
            }
            SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> {
                startIntentSender(state.resolutionIntent()?.intentSender, null, 0,0,0)
            }
            SplitInstallSessionStatus.INSTALLED -> {
                onSuccessfulLoad(names, launch = !multiInstall)
            }
            SplitInstallSessionStatus.INSTALLING -> displayLoadingState(state, "Installing $names")
            SplitInstallSessionStatus.FAILED -> {
                toastAndLog("Error: ${state.errorCode()} for module ${state.moduleNames()}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        manager = SplitInstallManagerFactory.create(this)
        initializeViews()
    }

    override fun onResume() {
        manager.registerListener(installListener)
        super.onResume()
    }

    override fun onPause() {
        manager.unregisterListener(installListener)
        super.onPause()
    }

    private fun loadAndLaunchModule(name: String) {
        updateProgressMessage("Loading module $name")
        if (manager.installedModules.contains(name)) {
            updateProgressMessage("Already installed.")
            onSuccessfulLoad(name, launch = true)
            return
        }

        val request = SplitInstallRequest.newBuilder()
                .addModule(name)
                .build()
        manager.startInstall(request)
        updateProgressMessage("Starting install for $name")
    }

    private fun installAllFeaturesNow() {
        val moduleNames = listOf(moduleAssets, moduleJava, moduleKotlin, moduleNative)
        val requestBuilder = SplitInstallRequest.newBuilder()
        moduleNames.forEach { name ->
            if (!manager.installedModules.contains(name)) {
                requestBuilder.addModule(name)
            }
        }
        val request = requestBuilder.build()
        manager.startInstall(request)
                .addOnSuccessListener {
                    toastAndLog("Loading ${request.moduleNames}")
                }
                .addOnFailureListener {
                    toastAndLog("Failed loading ${request.moduleNames}")
                }
    }

    private fun installAllFeaturesDeferred() {
        val modules = listOf(moduleAssets, moduleJava, moduleKotlin, moduleNative)
        manager.deferredInstall(modules).addOnSuccessListener {
            toastAndLog("Deferred installation of $modules")
        }.addOnFailureListener {
            toastAndLog("Failed installation of $modules")
        }
    }

    private fun requestUninstall() {
        toastAndLog("Requesting uninstall of all modules." +
                "This will happen at some point in the future.")
        val modules = listOf(moduleAssets, moduleJava, moduleKotlin, moduleNative)
        val installedModules = manager.installedModules
        manager.deferredUninstall(modules).addOnSuccessListener {
            toastAndLog("Uninstalling $installedModules")
        }
    }

    private val moduleAssets by lazy { getString(R.string.module_assets) }
    private val moduleKotlin by lazy { getString(R.string.module_feature_kotlin) }
    private val moduleJava by lazy { getString(R.string.module_feature_java) }
    private val moduleNative by lazy { getString(R.string.module_native) }

    /** Display assets loaded from the assets feature module. */
    /** manual implementation  version */
    private fun displayAssetsManually() {
        updateProgressMessage("Loading module $moduleAssets")
        if (manager.installedModules.contains(moduleAssets)) {
            updateProgressMessage("Already installed")
            // Get the asset manager with a refreshed context, to access content of newly installed apk.
            val assetManager = createPackageContext(packageName, 0).assets
            // Now treat it like any other asset file.
            val assets = assetManager.open("assets.txt")
            val assetContent = assets.bufferedReader()
                    .use {
                        it.readText()
                    }

            AlertDialog.Builder(this)
                    .setTitle("Asset content")
                    .setMessage(assetContent)
                    .show()
        } else {
            updateProgressMessage("Starting install for $moduleAssets")

            val request = SplitInstallRequest.newBuilder()
                    .addModule(moduleAssets)
                    .build()
            manager.startInstall(request)
                    .addOnCompleteListener {
                        displayAssets()
                    }
                    .addOnSuccessListener {
                        toastAndLog("loading $moduleAssets")
                    }
                    .addOnFailureListener {
                        toastAndLog("Error loading $moduleAssets")
                        displayButtons()
                    }
        }
    }

    private fun displayAssets() {
        // Get the asset manager with a refreshed context, to access content of newly installed apk.
        val assetManager = createPackageContext(packageName, 0).assets
        // Now treat it like any other asset file.
        val assets = assetManager.open("assets.txt")
        val assetContent = assets.bufferedReader()
                .use {
                    it.readText()
                }

        AlertDialog.Builder(this)
                .setTitle("Asset content")
                .setMessage(assetContent)
                .show()
    }

    /** Launch an activity by its class name. */
    private fun launchActivity(className: String) {
        Intent().setClassName(packageName, className)
                .also {
                    startActivity(it)
                }
    }

    /** Set up all view variables. */
    private fun initializeViews() {
        buttons = findViewById(R.id.buttons)
        progress = findViewById(R.id.progress)
        progressBar = findViewById(R.id.progress_bar)
        progressText = findViewById(R.id.progress_text)
        setupClickListener()
    }

    /** Set all click listeners required for the buttons on the UI. */
    private fun setupClickListener() {
        setClickListener(R.id.btn_load_kotlin, clickListener)
        setClickListener(R.id.btn_load_java, clickListener)
        setClickListener(R.id.btn_load_assets, clickListener)
        setClickListener(R.id.btn_load_native, clickListener)
        setClickListener(R.id.btn_install_all_now, clickListener)
        setClickListener(R.id.btn_install_all_deferred, clickListener)
        setClickListener(R.id.btn_request_uninstall, clickListener)
    }

    private fun setClickListener(id: Int, listener: View.OnClickListener) {
        findViewById<View>(id).setOnClickListener(listener)
    }

    private fun updateProgressMessage(message: String) {
        if (progress.visibility != View.VISIBLE) displayProgress()
        progressText.text = message
    }

    private fun displayProgress() {
        progress.visibility = View.VISIBLE
        buttons.visibility = View.GONE
    }

    private fun displayButtons() {
        progress.visibility = View.GONE
        buttons.visibility = View.VISIBLE
    }

    private fun displayLoadingState(state: SplitInstallSessionState, message: String) {
        displayProgress()

        progressBar.max = state.totalBytesToDownload().toInt()
        progressBar.progress = state.bytesDownloaded().toInt()

        updateProgressMessage(message)
    }

    private fun onSuccessfulLoad(moduleName: String, launch: Boolean) {
        if (launch) {
            when (moduleName) {
                moduleKotlin -> launchActivity(kotlinSampleClassname)
                moduleJava -> launchActivity(javaSampleClassname)
                moduleNative -> launchActivity(nativeSampleClassname)
                moduleAssets -> displayAssets()
            }
        }
        displayButtons()
    }
}

fun MainActivity.toastAndLog(text: String) {
    Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    Log.d(TAG, text)
}

private const val TAG = "DynamicFeatures"
