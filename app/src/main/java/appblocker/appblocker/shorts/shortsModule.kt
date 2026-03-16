package appblocker.appblocker.shorts

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val shortsModule = module {
    single { ShortsDb.get(androidContext()) }
    single { get<ShortsDb>().sessions() }
    single { get<ShortsDb>().schedules() }
    viewModel { ShortsViewModel(get(), get(), get()) }
}