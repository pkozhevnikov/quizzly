import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EditexamComponent } from './editexam.component';

describe('EditexamComponent', () => {
  let component: EditexamComponent;
  let fixture: ComponentFixture<EditexamComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ EditexamComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(EditexamComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
