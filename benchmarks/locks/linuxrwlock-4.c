#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <pthread.h>
#include <stdatomic.h>
#include <assert.h>

// linuxrwlocks.c
//

# define mo_relaxed memory_order_relaxed
# define mo_acquire memory_order_acquire
# define mo_release memory_order_release

#ifdef SPINLOOP_ASSUME
void __VERIFIER_assume(int);
#endif

typedef union {
    atomic_int lock;
} rwlock_t;

static inline int read_can_lock(rwlock_t *lock)
{
    return atomic_load_explicit(&lock->lock, mo_relaxed) > 0;
}

static inline int write_can_lock(rwlock_t *lock)
{
    return atomic_load_explicit(&lock->lock, mo_relaxed) == RW_LOCK_BIAS;
}

static inline void read_lock(rwlock_t *rw)
{
    int priorvalue = atomic_fetch_sub_explicit(&rw->lock, 1, mo_acquire);
    while (priorvalue <= 0) {
        atomic_fetch_add_explicit(&rw->lock, 1, mo_relaxed);
#ifdef SPINLOOP_ASSUME
        __VERIFIER_assume(atomic_load_explicit(&rw->lock, mo_relaxed) > 0);
#else
        while (atomic_load_explicit(&rw->lock, mo_relaxed) <= 0)
            ; //thrd_yield();
#endif
        priorvalue = atomic_fetch_sub_explicit(&rw->lock, 1, mo_acquire);
    }
}

static inline void write_lock(rwlock_t *rw)
{
    int priorvalue = atomic_fetch_sub_explicit(&rw->lock, RW_LOCK_BIAS, mo_acquire);
    while (priorvalue != RW_LOCK_BIAS) {
        atomic_fetch_add_explicit(&rw->lock, RW_LOCK_BIAS, mo_relaxed);
#ifdef SPINLOOP_ASSUME
        __VERIFIER_assume(atomic_load_explicit(&rw->lock, mo_relaxed) == RW_LOCK_BIAS);
#else
        while (atomic_load_explicit(&rw->lock, mo_relaxed) != RW_LOCK_BIAS)
            ; //thrd_yield();
#endif
        priorvalue = atomic_fetch_sub_explicit(&rw->lock, RW_LOCK_BIAS, mo_acquire);
    }
}

static inline int read_trylock(rwlock_t *rw)
{
    int priorvalue = atomic_fetch_sub_explicit(&rw->lock, 1, mo_acquire);
    if (priorvalue > 0)
        return 1;

    atomic_fetch_add_explicit(&rw->lock, 1, mo_relaxed);
    return 0;
}

static inline int write_trylock(rwlock_t *rw)
{
    int priorvalue = atomic_fetch_sub_explicit(&rw->lock, RW_LOCK_BIAS, mo_acquire);
    if (priorvalue == RW_LOCK_BIAS)
        return 1;

    atomic_fetch_add_explicit(&rw->lock, RW_LOCK_BIAS, mo_relaxed);
    return 0;
}

static inline void read_unlock(rwlock_t *rw)
{
    atomic_fetch_add_explicit(&rw->lock, 1, mo_release);
}

static inline void write_unlock(rwlock_t *rw)
{
    atomic_fetch_add_explicit(&rw->lock, RW_LOCK_BIAS, mo_release);
}

rwlock_t mylock;
int shareddata;

void *threadR(void *arg)
{
    read_lock(&mylock);
    int r = shareddata;
    read_unlock(&mylock);
    return NULL;
}

void *threadW(void *arg)
{
    write_lock(&mylock);
    shareddata = 42;
    write_unlock(&mylock);
    return NULL;
}

void *threadRW(void *arg)
{
    for (int i = 0; i < 2; i++) {
        if ((i % 2) == 0) {
            read_lock(&mylock);
            int r = shareddata;
            read_unlock(&mylock);
        } else {
            write_lock(&mylock);
            shareddata = i;
            write_unlock(&mylock);
        }
    }
    return NULL;
}

// variant
//
int main()
{
    pthread_t W0, W1, R0, R1;

    atomic_init(&mylock.lock, RW_LOCK_BIAS);
    
    pthread_create(&W0, NULL, threadW, NULL);
    pthread_create(&W1, NULL, threadW, NULL);
    
    pthread_create(&R0, NULL, threadR, NULL);
    pthread_create(&R1, NULL, threadR, NULL);

    return 0;
}
