import { DicomConnectionFormaterPipe } from './dicom-connection-formater.pipe';

describe('DicomConnectionFormaterPipe', () => {
  it('create an instance', () => {
    const pipe = new DicomConnectionFormaterPipe();
    expect(pipe).toBeTruthy();
  });
});
