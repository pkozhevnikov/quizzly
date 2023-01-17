import { Input, Output, EventEmitter, Component, OnInit } from '@angular/core'
import { FormControl } from "@angular/forms"
import { startWith, map, switchMap } from "rxjs"

import { Person, PersonsState } from "../../persons.state"

@Component({
  selector: 'app-personchooser',
  templateUrl: './personchooser.component.html',
  styleUrls: ['./personchooser.component.css']
})
export class PersonchooserComponent implements OnInit {
  
  @Input() chosen: Person[] = []
  @Output() chosenChange = new EventEmitter<Person[]>()
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
    this.chosenChange.emit(this.chosen)
  }

  add() {
    this.toChoose.filter(p => !this.chosen.includes(p)).forEach(p => this.chosen.push(p))
    this.toChoose = []
    this.chosenChange.emit(this.chosen)
  }

}
