{
  description = "tiny-matrix: 4x4 matrix accelerator for mpc-frame";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-24.05";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs { inherit system; };
      in {
        devShells.default = pkgs.mkShell {
          name = "tiny-matrix";
          packages = with pkgs; [
            mill
            zulu17
            circt
            # mpc-frame docs ask for 5.050; nixpkgs 24.05 ships a close 5.x.
            # Prefer an externally installed 5.050 on PATH when available.
            verilator
            python3
            gnumake
            git
            gtkwave
            gcc
          ];

          JAVA_HOME = pkgs.zulu17.home;

          shellHook = ''
            export JAVA_HOME=${pkgs.zulu17.home}
            export PATH="$JAVA_HOME/bin:$PATH"
            printf 'tiny-matrix nix shell\n'
            printf '  mill      %s\n' "$(mill --version 2>/dev/null | head -n 1 || echo missing)"
            printf '  java      %s\n' "$(java -version 2>&1 | head -n 1)"
            printf '  firtool   %s\n' "$(firtool --version 2>/dev/null | head -n 1 || echo missing)"
            printf '  verilator %s\n' "$(verilator --version 2>/dev/null | head -n 1 || echo missing)"
          '';
        };
      });
}
