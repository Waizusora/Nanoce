#include "nanoce/nanoce.hpp"

#include <cassert>

int main() {
    assert(nanoce::version() == "0.1.0-dev");
    return 0;
}
