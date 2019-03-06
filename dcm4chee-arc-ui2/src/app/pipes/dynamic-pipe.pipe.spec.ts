import { DynamicPipePipe } from './dynamic-pipe.pipe';
import {Injector} from "@angular/core";

describe('DynamicPipePipe', () => {
  it('create an instance', () => {
    let injector:Injector;
    const pipe = new DynamicPipePipe(injector);
    expect(pipe).toBeTruthy();
  });
});
