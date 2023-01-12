import { ComponentFixture, TestBed } from '@angular/core/testing';

import { NewexamComponent } from './newexam.component';

describe('NewexamComponent', () => {
  let component: NewexamComponent;
  let fixture: ComponentFixture<NewexamComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ NewexamComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(NewexamComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
