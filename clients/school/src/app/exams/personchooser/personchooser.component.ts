import { Output, EventEmitter, Component, OnInit } from '@angular/core'
import { FormControl } from "@angular/forms"
import { startWith, map, switchMap } from "rxjs"

import { Person, PersonsState } from "../../persons.state"

@Component({
  selector: 'app-personchooser',
  templateUrl: './personchooser.component.html',
  styleUrls: ['./personchooser.component.css']
})
export class PersonchooserComponent implements OnInit {
  
  chosen: Person[] = []
  @Output() onChange = new EventEmitter<Person[]>()
  toChoose: Person[] = []
  filter = new FormControl("")
  list$ = this.filter.valueChanges
    .pipe(startWith(""))
    .pipe(map(v => (typeof v === "string") ? v : ""))
    .pipe(switchMap(v => this.personsState.selectAll(v)))

  constructor(private personsState: PersonsState) { }

  ngOnInit(): void {
  }

  remove(id: string) {
    this.chosen = this.chosen.filter(p => p.id != id)
    this.onChange.emit(this.chosen)
  }

  add() {
    this.chosen = [...this.toChoose]
    this.onChange.emit(this.chosen)
  }
}
