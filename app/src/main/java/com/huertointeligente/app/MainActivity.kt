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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.huertointeligente.app.databinding.ActivityMainBinding
import com.huertointeligente.app.ui.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private val vm: MainViewModel by viewModels()

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* sin acción */ }

    private val locationPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
                    (permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
            if (granted) {
                startLocationUpdates()
            }
        }

    private val topLevelDestinations = setOf(
        R.id.homeFragment,
        R.id.historyFragment,
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

        // Botón Central Sobresaliente de IA con Animación
        binding.fabAi.setOnClickListener {
            if (navController.currentDestination?.id != R.id.aiFragment) {
                navController.navigate(R.id.aiFragment)
            }
        }
        startAiButtonAnimation()

        // Mostrar/ocultar barras según el destino
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val isTop = destination.id in topLevelDestinations
            binding.bottomNav.visibility = android.view.View.VISIBLE
            binding.toolbar.menu.findItem(R.id.action_switch_huerto)?.isVisible = isTop
            binding.toolbar.menu.findItem(R.id.action_settings)?.isVisible = isTop
            binding.fabAi.isSelected = (destination.id == R.id.aiFragment)
        }

        observeState()
        observeAutoSwitchEvents()
        askNotificationPermission()
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        askLocationPermission()
    }

    private fun askLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            startLocationUpdates()
        } else {
            locationPermission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                vm.onLocationUpdated(it.latitude, it.longitude)
            }
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10_000L)
            .setMinUpdateIntervalMillis(5_000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (loc in result.locations) {
                    vm.onLocationUpdated(loc.latitude, loc.longitude)
                }
            }
        }
        fusedLocationClient.requestLocationUpdates(request, locationCallback!!, mainLooper)
    }

    private fun observeAutoSwitchEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.autoSwitchEvent.collect { message ->
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
                            .setAnchorView(binding.bottomNav)
                            .show()
                    }
                }
                launch {
                    vm.lotPromptEvent.collect { targetHuerto ->
                        MaterialAlertDialogBuilder(this@MainActivity)
                            .setTitle("Cambio de Huerto Detectado")
                            .setMessage("Detectamos que estás en el huerto ${targetHuerto.name}. ¿Deseas cambiar la vista a este huerto?")
                            .setPositiveButton("Aceptar") { dialog, _ ->
                                vm.selectHuerto(targetHuerto.id)
                                dialog.dismiss()
                            }
                            .setNegativeButton("Cancelar", null)
                            .show()
                    }
                }
            }
        }
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

    private fun startAiButtonAnimation() {
        val fab = binding.fabAi
        val scaleX = android.animation.ObjectAnimator.ofFloat(fab, "scaleX", 1f, 1.12f, 1f).apply {
            duration = 1400
            repeatCount = android.animation.ObjectAnimator.INFINITE
            repeatMode = android.animation.ObjectAnimator.RESTART
        }
        val scaleY = android.animation.ObjectAnimator.ofFloat(fab, "scaleY", 1f, 1.12f, 1f).apply {
            duration = 1400
            repeatCount = android.animation.ObjectAnimator.INFINITE
            repeatMode = android.animation.ObjectAnimator.RESTART
        }
        android.animation.AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            start()
        }
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
        vm.setForeground(value = true)
    }

    override fun onStop() {
        vm.setForeground(value = false)
        super.onStop()
    }

    override fun onDestroy() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        super.onDestroy()
    }
}
