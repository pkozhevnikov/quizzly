import { Component, OnInit } from '@angular/core';
import { Person } from "../../persons.state"

@Component({
  selector: 'app-newexam',
  templateUrl: './newexam.component.html',
  styleUrls: ['./newexam.component.css']
})
export class NewexamComponent implements OnInit {

  selectedPersons: Person[] = []

  constructor() { }

  ngOnInit(): void {
  }

}
