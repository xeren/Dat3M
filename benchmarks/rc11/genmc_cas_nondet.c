#include <stdatomic.h>
#include <stdlib.h>
#include <pthread.h>
#include <assert.h>

atomic_int r = 0;
atomic_int rw1 = 1;
atomic_int rw2 = 2;

int cas1;
int cas2;

void *worker1(void *arg)
{
    int r2 = __VERIFIER_nondet_int();
    cas1 = atomic_compare_exchange_strong(&r, &r2, rw1);

    return NULL;
}

void *worker2(void *arg)
{
    int r2 = atomic_load(&r);
    cas2 = atomic_compare_exchange_strong(&r, &r2, rw2);

    return NULL;
}

int main()
{
    pthread_t t[2];

    pthread_create(&t[0], 0, worker1, NULL);
    pthread_create(&t[1], 0, worker2, NULL);

    pthread_join(t[0], 0);
    pthread_join(t[1], 0);

    if (cas1 && cas2) {
        assert(r != 1);
    }

    return 0;
}
