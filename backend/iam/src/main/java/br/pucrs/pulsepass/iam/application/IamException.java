package br.pucrs.pulsepass.iam.application;

public class IamException extends RuntimeException {
  private final int status;

  public IamException(int status, String message) {
    super(message);
    this.status = status;
  }

  public int status() {
    return status;
  }
}
