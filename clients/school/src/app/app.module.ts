import { NgModule } from "@angular/core"
import { BrowserModule } from "@angular/platform-browser"
import { HttpClientModule } from "@angular/common/http"
import { FormsModule, ReactiveFormsModule } from "@angular/forms"

import { AkitaNgDevtools } from "@datorama/akita-ngdevtools"
import { environment } from "../environments/environment"

import { AppRoutingModule } from "./app-routing.module"
import { SessionModule } from "./session/session.module"
import { QuizzesModule } from "./quizzes/quizzes.module"
import { ExamsModule } from "./exams/exams.module"
import { AppComponent } from "./app.component"

import { UiStore } from "./ui.store"
import { UiQuery } from "./ui.query"
import { PersonsState } from "./persons.state"
import { formatDate, DATE_PIPE_DEFAULT_TIMEZONE } from "@angular/common" 
import { GlobalConfig } from "./global.config"

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
    {provide: DATE_PIPE_DEFAULT_TIMEZONE, useValue: "UTC"},
    {provide: GlobalConfig, useValue: {baseApiUrl: "http://localhost:9099/v1"}},
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
