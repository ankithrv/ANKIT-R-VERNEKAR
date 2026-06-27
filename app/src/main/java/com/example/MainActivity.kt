package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.data.database.TrackDatabase
import com.example.data.repository.TrackRepository
import com.example.ui.TrackAppUi
import com.example.ui.TrackViewModel
import com.example.ui.TrackViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Local Database, Dao and Repository
        val database = TrackDatabase.getDatabase(applicationContext)
        val repository = TrackRepository(database.trackDao())

        // Create ViewModel
        val viewModel: TrackViewModel by viewModels {
            TrackViewModelFactory(repository)
        }

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    TrackAppUi(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

