import { Injectable } from '@angular/core'
import { Store, StoreConfig } from '@datorama/akita'

export class Notif {
  constructor(
    public kind: "info" | "warn" | "error" | "none",
    public text: string
  ) {}
  static EMPTY = new Notif("none", "")
  static info(text: string) {
    return new Notif("info", text)
  }
  static warn(text: string) {
    return new Notif("warn", text)
  }
  static error(text: string) {
    return new Notif("error", text)
  }
}

export interface UiState {
  notif: Notif
}

export function createInitialState(): UiState {
  return {
    notif: Notif.EMPTY
  }
}

@Injectable({ providedIn: 'root' })
@StoreConfig({ name: 'ui' })
export class UiStore extends Store<UiState> {

  constructor() {
    super(createInitialState())
  }

  info(text: string) {
    this.update({notif: Notif.info(text)})
  }
  warn(text: string) {
    this.update({notif: Notif.warn(text)})
  }
  error(text: string) {
    this.update({notif: Notif.error(text)})
  }

}
