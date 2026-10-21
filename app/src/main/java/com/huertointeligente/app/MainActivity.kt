package com.huertointeligente.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.huertointeligente.app.databinding.ActivityMainBinding
import com.huertointeligente.app.ui.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private val vm: MainViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* sin acción */ }

    private val topLevelDestinations = setOf(
        R.id.homeFragment,
        R.id.historyFragment,
        R.id.irrigationFragment,
        R.id.cropsFragment,
        R.id.alertsFragment,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        navController = navHost.navController

        // Barra superior con menú y flecha de regreso automática
        binding.toolbar.inflateMenu(R.menu.toolbar_menu)
        updateThemeIcon()
        
        binding.toolbar.setupWithNavController(navController, AppBarConfiguration(topLevelDestinations))
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_toggle_theme -> {
                    toggleTheme()
                    true
                }
                R.id.action_switch_huerto -> {
                    showHuertoPicker()
                    true
                }
                R.id.action_settings -> {
                    navController.navigate(R.id.settingsFragment)
                    true
                }
                else -> false
            }
        }

        // Navegación inferior
        binding.bottomNav.setupWithNavController(navController)

        // Mostrar/ocultar barras según el destino
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val isTop = destination.id in topLevelDestinations
            binding.bottomNav.visibility = if (isTop) android.view.View.VISIBLE else android.view.View.GONE
            binding.toolbar.menu.findItem(R.id.action_switch_huerto)?.isVisible = isTop
            binding.toolbar.menu.findItem(R.id.action_settings)?.isVisible = isTop
        }

        observeState()
        askNotificationPermission()
    }

    private fun toggleTheme() {
        val isDark = !vm.prefs.isDarkMode
        vm.prefs.isDarkMode = isDark
        val mode = if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        AppCompatDelegate.setDefaultNightMode(mode)
        updateThemeIcon()
    }

    private fun updateThemeIcon() {
        val item = binding.toolbar.menu.findItem(R.id.action_toggle_theme) ?: return
        if (vm.prefs.isDarkMode) {
            item.setIcon(R.drawable.ic_sun)
        } else {
            item.setIcon(R.drawable.ic_moon)
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.selected.collect { huerto ->
                        binding.toolbar.subtitle = huerto?.name
                    }
                }
                launch {
                    vm.unread.collect { count ->
                        if (count > 0) {
                            val badge = binding.bottomNav.getOrCreateBadge(R.id.alertsFragment)
                            badge.isVisible = true
                            badge.number = count
                        } else {
                            binding.bottomNav.removeBadge(R.id.alertsFragment)
                        }
                    }
                }
            }
        }
    }

    private fun showHuertoPicker() {
        val list = vm.huertos.value
        if (list.isEmpty()) {
            navController.navigate(R.id.huertosFragment)
            return
        }
        val names = list.map { it.name }.toTypedArray()
        val checked = list.indexOfFirst { it.id == vm.selected.value?.id }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.huerto_switch_title)
            .setSingleChoiceItems(names, checked) { dialog, which ->
                vm.selectHuerto(list[which].id)
                dialog.dismiss()
            }
            .setNeutralButton(R.string.huerto_manage) { _, _ ->
                navController.navigate(R.id.huertosFragment)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun askNotificationPermission() {
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) &&
            vm.prefs.notificationsEnabled &&
            (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED)
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onStart() {
        super.onStart()
        vm.setForeground(true)
    }

    override fun onStop() {
        vm.setForeground(false)
        super.onStop()
    }
}
