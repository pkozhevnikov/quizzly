import { NgModule } from "@angular/core"
import { BrowserModule } from "@angular/platform-browser"
import { HttpClientModule } from "@angular/common/http"
import { FormsModule, ReactiveFormsModule } from "@angular/forms"

import { AkitaNgDevtools } from "@datorama/akita-ngdevtools"
import { environment } from "../environments/environment"

import { AppRoutingModule } from "./app-routing.module"
import { SessionModule } from "./session/session.module"
import { AppComponent } from "./app.component"

import { UiStore } from "./ui.store"
import { UiQuery } from "./ui.query"
import { PersonsState } from "./persons.state"

@NgModule({
  declarations: [
    AppComponent
  ],
  imports: [
    BrowserModule,
    HttpClientModule,
    FormsModule,
    ReactiveFormsModule,
    SessionModule,
    QuizzesModule,
    ExamsModule,
    AppRoutingModule,
    environment.production ? [] : AkitaNgDevtools.forRoot()
  ],
  providers: [
    PersonsState,
    UiStore,
    UiQuery,
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
