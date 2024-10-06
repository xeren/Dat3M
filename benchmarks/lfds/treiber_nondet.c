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

    int i = __VERIFIER_nondet_int();
    if (i == 0) {
	exit(0);
    }

    pthread_create(&t[0], 0, worker, (void *)(size_t)1);
    pthread_create(&t[1], 0, worker, (void *)(size_t)i);
    pthread_create(&t[2], 0, worker, (void *)(size_t)2);

    for (int i = 0; i < NTHREADS; i++)
        pthread_join(t[i], 0);

    int r = pop();

    assert(r != 0);

    return 0;
}
