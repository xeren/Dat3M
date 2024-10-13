#include "treiber.h"
#include <pthread.h>
#include <assert.h>

#ifndef NTHREADS
#define NTHREADS 3
#endif

void *worker(void *arg)
{

    intptr_t index = ((intptr_t) arg);
    push(index);

    return NULL;
}

int main()
{
    pthread_t t[NTHREADS];

    init();

    int n = __VERIFIER_nondet_int();
    if (n == 0) {
	exit(0);
    }

    for (int i = 0; i < NTHREADS; i++) {
        if (i == 0) {
            pthread_create(&t[i], 0, worker, (void *)(size_t)n);
        } else {
            pthread_create(&t[i], 0, worker, (void *)(size_t)i);
        }
    }

    for (int i = 0; i < NTHREADS; i++)
        pthread_join(t[i], 0);

    int r = pop();

    assert(r != 0);

    return 0;
}
